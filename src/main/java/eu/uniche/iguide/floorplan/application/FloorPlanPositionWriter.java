package eu.uniche.iguide.floorplan.application;

import eu.uniche.iguide.floorplan.application.FloorPlanPositionUseCase.Wanted;
import eu.uniche.iguide.floorplan.domain.FloorPlanPosition;
import eu.uniche.iguide.floorplan.infrastructure.FloorPlanPositionRepository;
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
import org.springframework.stereotype.Component;

/**
 * The rules and row writes of a Floor Plan's positions, shared by the position endpoints and the apply of a
 * working copy. Every method runs inside the caller's transaction, without a status check.
 */
@Component
public class FloorPlanPositionWriter {

    static final String DUPLICATE = "The resource is already on this floor plan.";

    private final SpatialRules spatial;
    private final FloorPlanPositionRepository positions;

    public FloorPlanPositionWriter(SpatialRules spatial, FloorPlanPositionRepository positions) {
        this.spatial = spatial;
        this.positions = positions;
    }

    /**
     * Makes the plan's positions exactly {@code wanted}: the apply of a working copy (DECISIONS.md D-027). A wanted
     * entry keeps the position its {@code id} names, or else one of the same Resource, and moves it; the others are
     * positioned with the rules of {@link #position}; positions no entry claims are removed. Errors are reported at
     * {@code positions[i]}.
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
                throw new DuplicateValueException("positions[" + i + "].resourceId", DUPLICATE);
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
                    ? move(existing, w.x(), w.y(), w.rotation())
                    : position(projectId, planId, w.resourceType(), w.resourceId(), w.x(), w.y(), w.rotation())));
        }
        return result;
    }

    /** The checks of a new position other than the duplicate one: a Place or POI of the Project, the point. */
    void checkNew(UUID projectId, String resourceType, UUID resourceId, BigDecimal x, BigDecimal y, Integer rotation) {
        List<ErrorItem> errors = new ArrayList<>();
        spatial.checkTarget(projectId, resourceType, resourceId, errors);
        FloorPlanRules.checkPosition(x, y, rotation, errors);
        ResourceRules.throwIfAny(errors, "The position is invalid.");
    }

    static void checkMove(BigDecimal x, BigDecimal y, Integer rotation) {
        List<ErrorItem> errors = new ArrayList<>();
        FloorPlanRules.checkPosition(x, y, rotation, errors);
        ResourceRules.throwIfAny(errors, "The position is invalid.");
    }

    FloorPlanPosition position(UUID projectId, UUID planId, String resourceType, UUID resourceId,
                               BigDecimal x, BigDecimal y, Integer rotation) {
        checkNew(projectId, resourceType, resourceId, x, y, rotation);
        if (positions.existsByProjectIdAndFloorPlanIdAndResourceTypeAndResourceId(projectId, planId, resourceType,
                resourceId)) {
            throw new DuplicateValueException("resourceId", DUPLICATE);
        }
        return positions.saveAndFlush(FloorPlanPosition.of(projectId, planId, resourceType, resourceId, x, y,
                rotation));
    }

    FloorPlanPosition move(FloorPlanPosition position, BigDecimal x, BigDecimal y, Integer rotation) {
        checkMove(x, y, rotation);
        position.moveTo(x, y, rotation);
        return positions.saveAndFlush(position);
    }

    private static boolean sameTarget(FloorPlanPosition position, Wanted wanted) {
        return Objects.equals(position.getResourceType(), wanted.resourceType())
                && Objects.equals(position.getResourceId(), wanted.resourceId());
    }
}
