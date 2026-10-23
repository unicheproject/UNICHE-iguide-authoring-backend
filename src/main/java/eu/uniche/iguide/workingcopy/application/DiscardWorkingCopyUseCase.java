package eu.uniche.iguide.workingcopy.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import eu.uniche.iguide.workingcopy.infrastructure.ResourceWorkingCopyRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code DELETE .../{kind}/{id}/working-copy}: deletes the pending edit and nothing else ({@code 03} section 1).
 * The {@code RESOURCE_WORKING_COPY_DISCARDED} Audit Event waits for Milestone 7 (GAPS.md G-034).
 */
@Service
public class DiscardWorkingCopyUseCase {

    private final ProjectAccessGateway accessGateway;
    private final WorkingCopyHandlers handlers;
    private final ResourceWorkingCopyRepository copies;
    private final TransactionTemplate transactionTemplate;

    public DiscardWorkingCopyUseCase(ProjectAccessGateway accessGateway, WorkingCopyHandlers handlers,
                                     ResourceWorkingCopyRepository copies, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.handlers = handlers;
        this.copies = copies;
        this.transactionTemplate = transactionTemplate;
    }

    public void execute(UUID projectId, WorkingCopyResourceType type, UUID resourceId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        WorkingCopyHandler<Object> handler = handlers.handler(type);
        transactionTemplate.executeWithoutResult(status -> {
            handler.lock(projectId, resourceId);
            if (copies.deleteFor(projectId, type, resourceId) == 0) {
                throw new ResourceNotFoundException("This " + type.label() + " has no pending changes.");
            }
        });
    }
}
