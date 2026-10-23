package eu.uniche.iguide.floorplan.application;

import eu.uniche.iguide.floorplan.domain.FloorPlan;
import eu.uniche.iguide.floorplan.infrastructure.FloorPlanRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.workingcopy.application.WorkingCopyStore;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies one lifecycle transition. A plan without an image cannot become ACTIVE, since visitors would
 * have nothing to see (DECISIONS.md D-018). Nothing refers to a plan yet, and archival frees its Place.
 * Leaving ACTIVE discards the working copy ({@code 03} section 1).
 */
@Service
public class ChangeFloorPlanStatusUseCase {

    private final ProjectAccessGateway accessGateway;
    private final FloorPlanRules rules;
    private final FloorPlanRepository plans;
    private final WorkingCopyStore workingCopies;
    private final TransactionTemplate transactionTemplate;

    public ChangeFloorPlanStatusUseCase(ProjectAccessGateway accessGateway, FloorPlanRules rules,
                                        FloorPlanRepository plans, WorkingCopyStore workingCopies,
                                        TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.plans = plans;
        this.workingCopies = workingCopies;
        this.transactionTemplate = transactionTemplate;
    }

    public FloorPlan execute(UUID projectId, UUID planId, UnichePrincipal principal, ResourceStatus target,
                             Long version) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            FloorPlan plan = rules.requirePlan(projectId, planId);
            ResourceRules.requireCurrentVersion(plan.getVersion(), version, "floor plan");
            ResourceRules.requireTransition(plan.getStatus(), target, "floor plan");
            if (target == ResourceStatus.ACTIVE && plan.getAssetId() == null) {
                throw new ValidationFailedException("Required fields are missing.",
                        List.of(new ErrorItem("assetId", "MISSING_REQUIRED_FIELDS", "Choose the plan image first.")));
            }
            if (target != ResourceStatus.ACTIVE) {
                workingCopies.dropFor(projectId, WorkingCopyResourceType.FLOOR_PLAN, planId);
            }
            plan.changeStatus(target, principal.subject(), Instant.now());
            return plans.saveAndFlush(plan);
        });
    }
}
