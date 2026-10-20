package eu.uniche.iguide.floorplan.application;

import eu.uniche.iguide.asset.application.AssetReferenceSource;
import eu.uniche.iguide.floorplan.domain.FloorPlan;
import eu.uniche.iguide.floorplan.infrastructure.FloorPlanRepository;
import eu.uniche.iguide.resource.domain.ResourceReference;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Floor Plans that show an Asset as their image ({@code 07} section 14), named by their internal name. */
@Component
public class FloorPlanAssetReferences implements AssetReferenceSource {

    private final FloorPlanRepository plans;

    public FloorPlanAssetReferences(FloorPlanRepository plans) {
        this.plans = plans;
    }

    @Override
    public List<ResourceReference> referencesTo(UUID projectId, UUID assetId) {
        return plans.findByProjectIdAndAssetId(projectId, assetId).stream()
                .map(p -> new ResourceReference("FLOOR_PLAN", p.getId(), p.getInternalName(), p.getStatus()))
                .toList();
    }

    @Override
    public Map<UUID, Long> countsByAsset(UUID projectId, Collection<UUID> assetIds) {
        return plans.findByProjectIdAndAssetIdIn(projectId, assetIds).stream()
                .collect(Collectors.groupingBy(FloorPlan::getAssetId, Collectors.counting()));
    }

    @Override
    public String usage() {
        return "FLOOR_PLAN_IMAGE";
    }
}
