package eu.uniche.iguide.event.application;

import eu.uniche.iguide.event.domain.Event;
import eu.uniche.iguide.event.infrastructure.EventRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** {@code PUT .../events:order}: the complete ordered list of the Project's Event ids ({@code 07} section 14). */
@Service
public class ReorderEventsUseCase {

    private final ProjectAccessGateway accessGateway;
    private final EventRepository events;
    private final TransactionTemplate transactionTemplate;

    public ReorderEventsUseCase(ProjectAccessGateway accessGateway, EventRepository events,
                                TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.events = events;
        this.transactionTemplate = transactionTemplate;
    }

    public void execute(UUID projectId, UnichePrincipal principal, List<UUID> orderedIds) {
        accessGateway.requireAccess(projectId, principal);
        transactionTemplate.executeWithoutResult(status -> {
            Set<UUID> existing = events.findByProjectId(projectId).stream().map(Event::getId).collect(Collectors.toSet());
            List<UUID> ids = orderedIds == null ? List.of() : orderedIds;
            if (ids.size() != existing.size() || !new HashSet<>(ids).equals(existing)) {
                throw new ValidationFailedException("The order must list every event of the project exactly once.",
                        List.of(new ErrorItem("ids", "INCOMPLETE_ORDER", "List every event of this project exactly once.")));
            }
            for (int i = 0; i < ids.size(); i++) {
                events.updateSortOrder(projectId, ids.get(i), i + 1);
            }
        });
    }
}
