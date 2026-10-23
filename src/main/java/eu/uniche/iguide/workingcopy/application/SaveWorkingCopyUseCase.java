package eu.uniche.iguide.workingcopy.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.platform.web.VersionConflictException;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.workingcopy.domain.ResourceWorkingCopy;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import eu.uniche.iguide.workingcopy.infrastructure.ResourceWorkingCopyRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code PUT .../{kind}/{id}/working-copy} ({@code 07} section 14): creates or replaces the pending edit of an
 * ACTIVE Resource with the full payload. Visitors see nothing ({@code 03} section 1).
 *
 * <p>The payload is validated by a dry run: the kind's own write runs against the live rows in a transaction
 * that is always rolled back, so every rule of a normal save applies unchanged and the Resource as it would read
 * after apply comes back with the copy (DECISIONS.md D-027).
 *
 * <p>Versions: on creation {@code baseVersion} must be the live row's version. On replacement
 * {@code workingCopyVersion} must be the copy's version, and a {@code baseVersion} other than the copy's moves
 * the copy onto the live row, which is allowed only when it is the live row's version (the CMS does this after
 * an apply conflict, {@code 12} section 4). Anything else is {@code 409 VERSION_CONFLICT}.
 */
@Service
public class SaveWorkingCopyUseCase {

    private final ProjectAccessGateway accessGateway;
    private final WorkingCopyHandlers handlers;
    private final ResourceWorkingCopyRepository copies;
    private final TransactionTemplate transactionTemplate;

    public SaveWorkingCopyUseCase(ProjectAccessGateway accessGateway, WorkingCopyHandlers handlers,
                                  ResourceWorkingCopyRepository copies, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.handlers = handlers;
        this.copies = copies;
        this.transactionTemplate = transactionTemplate;
    }

    public WorkingCopyView execute(UUID projectId, WorkingCopyResourceType type, UUID resourceId,
                                   UnichePrincipal principal, Long baseVersion, Long workingCopyVersion,
                                   Map<String, Object> payload) {
        accessGateway.requireAccess(projectId, principal);
        WorkingCopyHandler<Object> handler = handlers.handler(type);
        Object state = handlers.read(handler, payload);
        // timestamptz keeps microseconds; the response must show what a later read returns.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        Object rendered = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            check(projectId, type, handler, resourceId, baseVersion, workingCopyVersion);
            handler.write(projectId, resourceId, principal, state);
            return handler.render(projectId, resourceId, now);
        });

        return transactionTemplate.execute(status -> {
            LiveResource live = check(projectId, type, handler, resourceId, baseVersion, workingCopyVersion);
            Map<String, Object> normalized = handlers.normalize(state);
            ResourceWorkingCopy copy = copies.findByProjectIdAndResourceTypeAndResourceId(projectId, type, resourceId)
                    .map(existing -> {
                        existing.replace(normalized, baseVersion, principal.subject(), now);
                        return existing;
                    })
                    .orElseGet(() -> ResourceWorkingCopy.create(projectId, type, resourceId, normalized, baseVersion,
                            principal.subject(), now));
            return new WorkingCopyView(copies.saveAndFlush(copy), live.version(), rendered, List.of());
        });
    }

    private LiveResource check(UUID projectId, WorkingCopyResourceType type, WorkingCopyHandler<?> handler,
                               UUID resourceId, Long baseVersion, Long workingCopyVersion) {
        LiveResource live = handler.lock(projectId, resourceId);
        requireActive(live, type);
        ResourceWorkingCopy existing = copies.findByProjectIdAndResourceTypeAndResourceId(projectId, type, resourceId)
                .orElse(null);
        if (existing != null && (workingCopyVersion == null || existing.getVersion() != workingCopyVersion)) {
            throw new VersionConflictException(
                    "The pending changes of this " + type.label() + " were saved by someone else since they were loaded.",
                    existing.getVersion());
        }
        boolean rebase = existing == null || baseVersion == null || existing.getBaseVersion() != baseVersion;
        if (rebase && (baseVersion == null || live.version() != baseVersion)) {
            throw new VersionConflictException("The " + type.label() + " was changed since it was loaded.",
                    live.version());
        }
        return live;
    }

    static void requireActive(LiveResource live, WorkingCopyResourceType type) {
        if (live.status() != ResourceStatus.ACTIVE) {
            throw new ValidationFailedException("Only an ACTIVE " + type.label() + " has pending changes.",
                    List.of(new ErrorItem("status", "RESOURCE_NOT_ACTIVE",
                            "A DRAFT " + type.label() + " is saved directly.")));
        }
    }
}
