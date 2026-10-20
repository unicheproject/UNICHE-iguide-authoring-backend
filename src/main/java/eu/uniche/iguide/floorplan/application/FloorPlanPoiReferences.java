package eu.uniche.iguide.floorplan.application;

import eu.uniche.iguide.floorplan.infrastructure.FloorPlanRepository;
import eu.uniche.iguide.poi.application.PoiReferenceSource;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.spatial.application.SpatialRules;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Floor Plans that position a Point of Interest ({@code 03} section 1: {@code FloorPlanPosition} rows). */
@Component
public class FloorPlanPoiReferences implements PoiReferenceSource {

    private final FloorPlanRepository plans;

    public FloorPlanPoiReferences(FloorPlanRepository plans) {
        this.plans = plans;
    }

    @Override
    public List<ResourceReference> referencesTo(UUID projectId, UUID poiId) {
        return plans.findPositioning(projectId, SpatialRules.POI, poiId).stream()
                .map(plan -> new ResourceReference("FLOOR_PLAN", plan.getId(), plan.getInternalName(), plan.getStatus()))
                .toList();
    }
}
