package eu.uniche.iguide.asset.application;

import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.asset.domain.ResourceAsset;
import eu.uniche.iguide.asset.infrastructure.ResourceAssetRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** The media bindings of several Resources of one kind, for their reads (INLINE rows included). */
@Component
public class ResourceMedia {

    private final ResourceAssetRepository bindings;

    public ResourceMedia(ResourceAssetRepository bindings) {
        this.bindings = bindings;
    }

    /** By Resource id, each list in {@code sortOrder}. */
    public Map<UUID, List<ResourceAsset>> of(UUID projectId, AssetResourceType kind, Collection<UUID> resourceIds) {
        if (resourceIds.isEmpty()) {
            return Map.of();
        }
        return bindings.findByProjectIdAndResourceTypeAndResourceIdInOrderBySortOrderAsc(projectId, kind, resourceIds)
                .stream().collect(Collectors.groupingBy(ResourceAsset::getResourceId));
    }
}
