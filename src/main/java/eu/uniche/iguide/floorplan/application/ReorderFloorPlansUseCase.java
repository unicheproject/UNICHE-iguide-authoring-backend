package eu.uniche.iguide.floorplan.application;

import eu.uniche.iguide.floorplan.domain.FloorPlan;
import eu.uniche.iguide.floorplan.infrastructure.FloorPlanRepository;
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

/**
 * {@code PUT .../floor-plans:order} ({@code 12} section 7): the complete ordered list of the Project's
 * Floor Plan ids. The order among one Place's plans is its floor switcher order, lowest floor first.
 */
@Service
public class ReorderFloorPlansUseCase {

    private final ProjectAccessGateway accessGateway;
    private final FloorPlanRepository plans;
    private final TransactionTemplate transactionTemplate;

    public ReorderFloorPlansUseCase(ProjectAccessGateway accessGateway, FloorPlanRepository plans,
                                    TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.plans = plans;
        this.transactionTemplate = transactionTemplate;
    }

    public void execute(UUID projectId, UnichePrincipal principal, List<UUID> orderedIds) {
        accessGateway.requireAccess(projectId, principal);
        transactionTemplate.executeWithoutResult(status -> {
            Set<UUID> existing = plans.findByProjectId(projectId).stream().map(FloorPlan::getId)
                    .collect(Collectors.toSet());
            List<UUID> ids = orderedIds == null ? List.of() : orderedIds;
            if (ids.size() != existing.size() || !new HashSet<>(ids).equals(existing)) {
                throw new ValidationFailedException("The order must list every floor plan of the project exactly once.",
                        List.of(new ErrorItem("ids", "INCOMPLETE_ORDER",
                                "List every floor plan of this project exactly once.")));
            }
            for (int i = 0; i < ids.size(); i++) {
                plans.updateSortOrder(projectId, ids.get(i), i + 1);
            }
        });
    }
}
