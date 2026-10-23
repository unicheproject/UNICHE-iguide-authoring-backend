package eu.uniche.iguide.workingcopy.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ApiException;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.workingcopy.domain.ResourceWorkingCopy;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import eu.uniche.iguide.workingcopy.infrastructure.ResourceWorkingCopyRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code GET .../{kind}/{id}/working-copy}: the pending edit the editor of an ACTIVE Resource opens with
 * ({@code 12} section 4), and the Resource as it would read after apply, rendered by the same dry run as a save
 * (DECISIONS.md D-027). The path is not in {@code 07} section 14 yet (QUESTIONS.md Q-016).
 */
@Service
public class GetWorkingCopyQuery {

    private final ProjectAccessGateway accessGateway;
    private final WorkingCopyHandlers handlers;
    private final ResourceWorkingCopyRepository copies;
    private final TransactionTemplate transactionTemplate;

    public GetWorkingCopyQuery(ProjectAccessGateway accessGateway, WorkingCopyHandlers handlers,
                               ResourceWorkingCopyRepository copies, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.handlers = handlers;
        this.copies = copies;
        this.transactionTemplate = transactionTemplate;
    }

    public WorkingCopyView execute(UUID projectId, WorkingCopyResourceType type, UUID resourceId,
                                   UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        WorkingCopyHandler<Object> handler = handlers.handler(type);
        return transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            LiveResource live = handler.lock(projectId, resourceId);
            ResourceWorkingCopy copy = copies.findByProjectIdAndResourceTypeAndResourceId(projectId, type, resourceId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "This " + type.label() + " has no pending changes."));
            try {
                handler.write(projectId, resourceId, principal, handlers.read(handler, copy.getPayload()));
                return new WorkingCopyView(copy, live.version(),
                        handler.render(projectId, resourceId, copy.getUpdatedAt()), List.of());
            } catch (ApiException e) {
                List<ErrorItem> errors = e.errors().isEmpty()
                        ? List.of(new ErrorItem("payload", e.type(), e.getMessage()))
                        : e.errors();
                return new WorkingCopyView(copy, live.version(), null, errors);
            }
        });
    }
}
