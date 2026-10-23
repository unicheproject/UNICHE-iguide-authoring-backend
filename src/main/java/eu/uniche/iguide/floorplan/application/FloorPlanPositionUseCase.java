package eu.uniche.iguide.floorplan.application;

import eu.uniche.iguide.floorplan.domain.FloorPlan;
import eu.uniche.iguide.floorplan.domain.FloorPlanPosition;
import eu.uniche.iguide.floorplan.infrastructure.FloorPlanPositionRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.DuplicateValueException;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ErrorPaths;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.spatial.application.SpatialRules;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Positions, moves, and removes Resources on a DRAFT Floor Plan ({@code 07} section 14,
 * {@code .../floor-plans/{id}/positions}). An ACTIVE plan changes its positions through its working copy, whose
 * apply calls {@link #replacePositions} (DECISIONS.md D-027); these endpoints refuse it.
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

    /** One wanted position of a working-copy payload; {@code id} names an existing position to keep. */
    public record Wanted(UUID id, String resourceType, UUID resourceId, BigDecimal x, BigDecimal y, Integer rotation) {
    }

    public FloorPlanPosition position(UUID projectId, UUID planId, UnichePrincipal principal, String resourceType,
                                      UUID resourceId, BigDecimal x, BigDecimal y, Integer rotation) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            requireDraftPlan(projectId, planId);
            return positionIn(projectId, planId, resourceType, resourceId, x, y, rotation);
        });
    }

    public FloorPlanPosition move(UUID projectId, UUID planId, UUID positionId, UnichePrincipal principal,
                                  BigDecimal x, BigDecimal y, Integer rotation) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            requireDraftPlan(projectId, planId);
            return moveIn(requirePosition(projectId, planId, positionId), x, y, rotation);
        });
    }

    public void remove(UUID projectId, UUID planId, UUID positionId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        transactionTemplate.executeWithoutResult(status -> {
            requireDraftPlan(projectId, planId);
            positions.delete(requirePosition(projectId, planId, positionId));
        });
    }

    /**
     * Makes the plan's positions exactly {@code wanted}, inside the caller's transaction and without a status
     * check: the apply of a working copy (DECISIONS.md D-027). A wanted entry keeps the position its {@code id}
     * names, or else one of the same Resource, and moves it; the others are positioned with the rules of
     * {@link #position}; positions no entry claims are removed. Errors are reported at {@code positions[i]}.
     */
    public List<FloorPlanPosition> replacePositions(UUID projectId, UUID planId, List<Wanted> wanted) {
        List<Wanted> desired = wanted == null ? List.of() : wanted;
        List<FloorPlanPosition> unclaimed = new ArrayList<>(
                positions.findByProjectIdAndFloorPlanIdOrderByIdAsc(projectId, planId));
        FloorPlanPosition[] kept = new FloorPlanPosition[desired.size()];
        Set<String> targets = new HashSet<>();
        for (int i = 0; i < desired.size(); i++) {
            Wanted w = desired.get(i);
            if (!targets.add(w.resourceType() + ":" + w.resourceId())) {
                throw new DuplicateValueException("positions[" + i + "].resourceId",
                        "The resource is already on this floor plan.");
            }
            FloorPlanPosition match = unclaimed.stream()
                    .filter(p -> w.id() != null && p.getId().equals(w.id()) && sameTarget(p, w))
                    .findFirst()
                    .or(() -> unclaimed.stream().filter(p -> sameTarget(p, w)).findFirst())
                    .orElse(null);
            if (match != null) {
                unclaimed.remove(match);
                kept[i] = match;
            }
        }
        positions.deleteAll(unclaimed);
        positions.flush();
        List<FloorPlanPosition> result = new ArrayList<>();
        for (int i = 0; i < desired.size(); i++) {
            Wanted w = desired.get(i);
            FloorPlanPosition existing = kept[i];
            result.add(ErrorPaths.within("positions[" + i + "]", () -> existing != null
                    ? moveIn(existing, w.x(), w.y(), w.rotation())
                    : positionIn(projectId, planId, w.resourceType(), w.resourceId(), w.x(), w.y(), w.rotation())));
        }
        return result;
    }

    private FloorPlanPosition positionIn(UUID projectId, UUID planId, String resourceType, UUID resourceId,
                                         BigDecimal x, BigDecimal y, Integer rotation) {
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
    }

    private FloorPlanPosition moveIn(FloorPlanPosition position, BigDecimal x, BigDecimal y, Integer rotation) {
        List<ErrorItem> errors = new ArrayList<>();
        FloorPlanRules.checkPosition(x, y, rotation, errors);
        ResourceRules.throwIfAny(errors, "The position is invalid.");
        position.moveTo(x, y, rotation);
        return positions.saveAndFlush(position);
    }

    private static boolean sameTarget(FloorPlanPosition position, Wanted wanted) {
        return Objects.equals(position.getResourceType(), wanted.resourceType())
                && Objects.equals(position.getResourceId(), wanted.resourceId());
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
