package eu.uniche.iguide.workingcopy.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.platform.web.VersionConflictException;
import eu.uniche.iguide.workingcopy.domain.ResourceWorkingCopy;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import eu.uniche.iguide.workingcopy.infrastructure.ResourceWorkingCopyRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code POST .../{kind}/{id}/working-copy:apply} ({@code 03} section 1, {@code 06} section 17): in one
 * transaction, reads the live row for update, refuses with {@code 409} and {@code currentVersion} when it moved
 * past {@code baseVersion}, writes the payload like a normal save, and deletes the copy. Returns the Resource as
 * it now reads.
 *
 * <p>Not yet: the {@code contentRevision} increment (no Project settings, GAPS.md G-024) and the
 * {@code RESOURCE_CHANGES_APPLIED} Audit Event (Milestone 7, GAPS.md G-023); see GAPS.md G-034.
 */
@Service
public class ApplyWorkingCopyUseCase {

    private final ProjectAccessGateway accessGateway;
    private final WorkingCopyHandlers handlers;
    private final ResourceWorkingCopyRepository copies;
    private final TransactionTemplate transactionTemplate;

    public ApplyWorkingCopyUseCase(ProjectAccessGateway accessGateway, WorkingCopyHandlers handlers,
                                   ResourceWorkingCopyRepository copies, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.handlers = handlers;
        this.copies = copies;
        this.transactionTemplate = transactionTemplate;
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
            handler.write(projectId, resourceId, principal, handlers.read(handler, copy.getPayload()));
            copies.delete(copy);
            copies.flush();
            return handler.render(projectId, resourceId, null);
        });
    }
}
