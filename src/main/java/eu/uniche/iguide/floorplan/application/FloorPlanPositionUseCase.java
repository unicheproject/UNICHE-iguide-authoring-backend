package eu.uniche.iguide.floorplan.application;

import eu.uniche.iguide.floorplan.domain.FloorPlan;
import eu.uniche.iguide.floorplan.domain.FloorPlanPosition;
import eu.uniche.iguide.floorplan.infrastructure.FloorPlanPositionRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.DuplicateValueException;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.spatial.application.SpatialRules;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Positions, moves, and removes Resources on a DRAFT Floor Plan ({@code 07} section 14,
 * {@code .../floor-plans/{id}/positions}).
 */
@Service
public class FloorPlanPositionUseCase {

    private final ProjectAccessGateway accessGateway;
    private final FloorPlanRules rules;
    private final SpatialRules spatial;
    private final FloorPlanPositionRepository positions;
    private final TransactionTemplate transactionTemplate;

    public FloorPlanPositionUseCase(ProjectAccessGateway accessGateway, FloorPlanRules rules, SpatialRules spatial,
                                    FloorPlanPositionRepository positions, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.spatial = spatial;
        this.positions = positions;
        this.transactionTemplate = transactionTemplate;
    }

    public FloorPlanPosition position(UUID projectId, UUID planId, UnichePrincipal principal, String resourceType,
                                      UUID resourceId, BigDecimal x, BigDecimal y, Integer rotation) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            requireDraftPlan(projectId, planId);
            List<ErrorItem> errors = new ArrayList<>();
            spatial.checkTarget(projectId, resourceType, resourceId, errors);
            FloorPlanRules.checkPosition(x, y, rotation, errors);
            ResourceRules.throwIfAny(errors, "The position is invalid.");
            if (positions.existsByProjectIdAndFloorPlanIdAndResourceTypeAndResourceId(projectId, planId, resourceType,
                    resourceId)) {
                throw new DuplicateValueException("resourceId", "The resource is already on this floor plan.");
            }
            return positions.saveAndFlush(FloorPlanPosition.of(projectId, planId, resourceType, resourceId, x, y,
                    rotation));
        });
    }

    public FloorPlanPosition move(UUID projectId, UUID planId, UUID positionId, UnichePrincipal principal,
                                  BigDecimal x, BigDecimal y, Integer rotation) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            requireDraftPlan(projectId, planId);
            FloorPlanPosition position = requirePosition(projectId, planId, positionId);
            List<ErrorItem> errors = new ArrayList<>();
            FloorPlanRules.checkPosition(x, y, rotation, errors);
            ResourceRules.throwIfAny(errors, "The position is invalid.");
            position.moveTo(x, y, rotation);
            return positions.saveAndFlush(position);
        });
    }

    public void remove(UUID projectId, UUID planId, UUID positionId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        transactionTemplate.executeWithoutResult(status -> {
            requireDraftPlan(projectId, planId);
            positions.delete(requirePosition(projectId, planId, positionId));
        });
    }

    private void requireDraftPlan(UUID projectId, UUID planId) {
        FloorPlan plan = rules.requirePlan(projectId, planId);
        ResourceRules.requireDraft(plan.getStatus(), "floor plan");
    }

    private FloorPlanPosition requirePosition(UUID projectId, UUID planId, UUID positionId) {
        return SpatialRules.require(positions.findByProjectIdAndFloorPlanIdAndId(projectId, planId, positionId),
                "position on this floor plan");
    }
}
