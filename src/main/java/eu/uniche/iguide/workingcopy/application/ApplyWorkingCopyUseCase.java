package eu.uniche.iguide.workingcopy.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.platform.web.VersionConflictException;
import eu.uniche.iguide.readiness.application.ReadinessIssue;
import eu.uniche.iguide.settings.application.ContentRevision;
import eu.uniche.iguide.workingcopy.domain.ResourceWorkingCopy;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import eu.uniche.iguide.workingcopy.infrastructure.ResourceWorkingCopyRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code POST .../{kind}/{id}/working-copy:apply} ({@code 03} section 1, {@code 06} section 17): in one
 * transaction, reads the live row for update, refuses with {@code 409} and {@code currentVersion} when it moved
 * past {@code baseVersion}, writes the payload like a normal save, and deletes the copy. Returns the Resource as
 * it now reads. The {@link WorkingCopyApplyHook}s carry what else the copy holds, such as the provenance of machine
 * translations (DECISIONS.md D-035). The write is refused with {@code 422}, and rolled back, when it would add
 * blocking readiness issues to the Project ({@code 14} section 2, {@link ApplyReadinessGate}, DECISIONS.md D-037).
 *
 * Apply raises {@code contentRevision} ({@link ContentRevision}).
 *
 * <p>Not yet: the {@code RESOURCE_CHANGES_APPLIED} Audit Event (Milestone 7, GAPS.md G-023); see GAPS.md G-034.
 */
@Service
public class ApplyWorkingCopyUseCase {

    private final ProjectAccessGateway accessGateway;
    private final WorkingCopyHandlers handlers;
    private final ResourceWorkingCopyRepository copies;
    private final List<WorkingCopyApplyHook> hooks;
    private final ApplyReadinessGate readinessGate;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final ContentRevision contentRevision;

    public ApplyWorkingCopyUseCase(ProjectAccessGateway accessGateway, WorkingCopyHandlers handlers,
                                   ResourceWorkingCopyRepository copies, List<WorkingCopyApplyHook> hooks,
                                   ApplyReadinessGate readinessGate, EntityManager entityManager,
                                   TransactionTemplate transactionTemplate,
                                   ContentRevision contentRevision) {
        this.accessGateway = accessGateway;
        this.handlers = handlers;
        this.copies = copies;
        this.hooks = List.copyOf(hooks);
        this.readinessGate = readinessGate;
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
        this.contentRevision = contentRevision;
    }

    public Object execute(UUID projectId, WorkingCopyResourceType type, UUID resourceId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        WorkingCopyHandler<Object> handler = handlers.handler(type);
        return transactionTemplate.execute(status -> {
            LiveResource live = handler.lock(projectId, resourceId);
            ResourceWorkingCopy copy = copies.findByProjectIdAndResourceTypeAndResourceId(projectId, type, resourceId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "This " + type.label() + " has no pending changes."));
            SaveWorkingCopyUseCase.requireActive(live, type);
            if (live.version() != copy.getBaseVersion()) {
                throw new VersionConflictException(
                        "The " + type.label() + " was changed since its pending changes were started.", live.version());
            }
            List<ReadinessIssue> before = readinessGate.blocking(projectId);
            handler.write(projectId, resourceId, principal, handlers.read(handler, copy.getPayload()));
            hooks.forEach(hook -> hook.applied(projectId, type, resourceId));
            copies.delete(copy);
            copies.flush();
            contentRevision.increment(projectId);
            // The evaluation reads through repositories and plain SQL: both must see the rows as written.
            entityManager.flush();
            entityManager.clear();
            readinessGate.requireNoNewBlocking(projectId, type, resourceId, before);
            return handler.render(projectId, resourceId, null);
        });
    }
}
