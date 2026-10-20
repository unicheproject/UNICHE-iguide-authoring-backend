package eu.uniche.iguide.asset.application;

import eu.uniche.iguide.resource.domain.ResourceReference;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rows outside {@code resource_assets} that use an Asset, such as a Floor Plan's image ({@code 07} section
 * 14), implemented in the referring package.
 */
public interface AssetReferenceSource {

    /** Every row that uses the Asset, archived ones included, so the where-used list is complete. */
    List<ResourceReference> referencesTo(UUID projectId, UUID assetId);

    /** The number of rows using each of the given Assets. */
    Map<UUID, Long> countsByAsset(UUID projectId, Collection<UUID> assetIds);

    /** The role these rows give the Asset in the where-used list, for example {@code FLOOR_PLAN_IMAGE}. */
    String usage();
}
