package eu.uniche.iguide.floorplan.application;

import eu.uniche.iguide.floorplan.domain.FloorPlan;
import eu.uniche.iguide.floorplan.domain.FloorPlan.Details;
import eu.uniche.iguide.floorplan.infrastructure.FloorPlanRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ResourceRules;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Creates a DRAFT Floor Plan, last in the Project's order. */
@Service
public class CreateFloorPlanUseCase {

    private final ProjectAccessGateway accessGateway;
    private final FloorPlanRules rules;
    private final FloorPlanRepository plans;
    private final TransactionTemplate transactionTemplate;

    public CreateFloorPlanUseCase(ProjectAccessGateway accessGateway, FloorPlanRules rules, FloorPlanRepository plans,
                                  TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.plans = plans;
        this.transactionTemplate = transactionTemplate;
    }

    public FloorPlan execute(UUID projectId, UnichePrincipal principal, Details details) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            List<ErrorItem> errors = new ArrayList<>();
            Details checked = rules.checkDetails(projectId, details, errors);
            ResourceRules.throwIfAny(errors, "The floor plan is invalid.");
            return plans.saveAndFlush(FloorPlan.draft(projectId, checked, plans.findMaxSortOrder(projectId) + 1,
                    principal.subject(), Instant.now()));
        });
    }
}
