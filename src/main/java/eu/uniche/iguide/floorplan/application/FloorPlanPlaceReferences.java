package eu.uniche.iguide.floorplan.application;

import eu.uniche.iguide.floorplan.domain.FloorPlan;
import eu.uniche.iguide.floorplan.infrastructure.FloorPlanRepository;
import eu.uniche.iguide.place.application.PlaceReferenceSource;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.spatial.application.SpatialRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Floor Plans that represent a Place or position it ({@code 03} section 1). */
@Component
public class FloorPlanPlaceReferences implements PlaceReferenceSource {

    private final FloorPlanRepository plans;

    public FloorPlanPlaceReferences(FloorPlanRepository plans) {
        this.plans = plans;
    }

    @Override
    public List<ResourceReference> referencesTo(UUID projectId, UUID placeId) {
        Map<UUID, FloorPlan> found = new LinkedHashMap<>();
        plans.findByProjectIdAndPlaceId(projectId, placeId).stream()
                .filter(plan -> plan.getStatus() != ResourceStatus.ARCHIVED)
                .forEach(plan -> found.put(plan.getId(), plan));
        plans.findPositioning(projectId, SpatialRules.PLACE, placeId)
                .forEach(plan -> found.putIfAbsent(plan.getId(), plan));
        return found.values().stream()
                .map(plan -> new ResourceReference("FLOOR_PLAN", plan.getId(), plan.getInternalName(), plan.getStatus()))
                .toList();
    }

    @Override
    public boolean linksPlace(UUID projectId, UUID placeId) {
        return !plans.findByProjectIdAndPlaceId(projectId, placeId).isEmpty();
    }
}
