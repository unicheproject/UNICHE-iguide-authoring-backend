package eu.uniche.iguide.floorplan.application;

import eu.uniche.iguide.floorplan.domain.FloorPlan;
import eu.uniche.iguide.floorplan.domain.FloorPlan.Details;
import eu.uniche.iguide.floorplan.infrastructure.FloorPlanRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Saves a DRAFT Floor Plan. A changed image keeps the positions, which are normalized. An ACTIVE plan is edited
 * through its working copy, whose save and apply reuse {@link #write} (DECISIONS.md D-027).
 */
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
            return write(projectId, plan, principal, details);
        });
    }

    /**
     * Validates and writes the details onto the plan, inside the caller's transaction, without a status or
     * version check: the DRAFT save above and the working copy of an ACTIVE plan share it. An ACTIVE plan keeps
     * an image, which visitors need.
     */
    public FloorPlan write(UUID projectId, FloorPlan plan, UnichePrincipal principal, Details details) {
        List<ErrorItem> errors = new ArrayList<>();
        Details checked = rules.checkDetails(projectId, details, errors);
        if (plan.getStatus() == ResourceStatus.ACTIVE && checked.assetId() == null) {
            errors.add(new ErrorItem("assetId", "MISSING_REQUIRED_FIELDS", "An ACTIVE floor plan keeps its image."));
        }
        ResourceRules.throwIfAny(errors, "The floor plan is invalid.");
        plan.edit(checked, principal.subject(), Instant.now());
        return plans.saveAndFlush(plan);
    }
}
