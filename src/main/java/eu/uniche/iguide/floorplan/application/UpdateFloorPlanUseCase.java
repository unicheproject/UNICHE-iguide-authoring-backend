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

/** Saves a DRAFT Floor Plan. A changed image keeps the positions, which are normalized. */
@Service
public class UpdateFloorPlanUseCase {

    private final ProjectAccessGateway accessGateway;
    private final FloorPlanRules rules;
    private final FloorPlanRepository plans;
    private final TransactionTemplate transactionTemplate;

    public UpdateFloorPlanUseCase(ProjectAccessGateway accessGateway, FloorPlanRules rules, FloorPlanRepository plans,
                                  TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.plans = plans;
        this.transactionTemplate = transactionTemplate;
    }

    public FloorPlan execute(UUID projectId, UUID planId, UnichePrincipal principal, Long version, Details details) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            FloorPlan plan = rules.requirePlan(projectId, planId);
            ResourceRules.requireCurrentVersion(plan.getVersion(), version, "floor plan");
            ResourceRules.requireDraft(plan.getStatus(), "floor plan");
            List<ErrorItem> errors = new ArrayList<>();
            Details checked = rules.checkDetails(projectId, details, errors);
            ResourceRules.throwIfAny(errors, "The floor plan is invalid.");
            plan.edit(checked, principal.subject(), Instant.now());
            return plans.saveAndFlush(plan);
        });
    }
}
