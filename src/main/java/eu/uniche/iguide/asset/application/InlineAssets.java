package eu.uniche.iguide.asset.application;

import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.asset.domain.AssetUsage;
import eu.uniche.iguide.asset.domain.ResourceAsset;
import eu.uniche.iguide.asset.infrastructure.ResourceAssetRepository;
import eu.uniche.iguide.platform.web.ErrorItem;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The {@code INLINE} bindings of rich text ({@code 03} section 4, {@code 07} section 10): every save of a
 * rich text field checks the embedded Asset ids and reconciles one {@code INLINE} row per Asset for the
 * owning Resource, inserting missing rows and deleting rows for ids no longer embedded. Runs inside the
 * caller's transaction.
 */
@Component
public class InlineAssets {

    private final AssetRules rules;
    private final ResourceAssetRepository bindings;

    public InlineAssets(AssetRules rules, ResourceAssetRepository bindings) {
        this.rules = rules;
        this.bindings = bindings;
    }

    /**
     * Adds an error at {@code path} unless the id names an image of this Project that is not archived; an id
     * from another Project is {@code RESOURCE_NOT_FOUND}.
     */
    public void check(UUID projectId, String assetId, String path, List<ErrorItem> errors) {
        UUID id;
        try {
            id = assetId == null ? null : UUID.fromString(assetId);
        } catch (IllegalArgumentException e) {
            errors.add(new ErrorItem(path, "RESOURCE_NOT_FOUND", "No such asset in this project."));
            return;
        }
        rules.checkBindable(projectId, id, AssetUsage.INLINE, path, errors);
    }

    /** Makes the Resource's {@code INLINE} rows exactly the given Assets. */
    public void reconcile(UUID projectId, AssetResourceType kind, UUID resourceId, Set<UUID> embedded) {
        List<ResourceAsset> current = bindings.findByProjectIdAndResourceTypeAndResourceIdAndUsage(projectId, kind,
                resourceId, AssetUsage.INLINE);
        List<ResourceAsset> stale = current.stream().filter(b -> !embedded.contains(b.getAssetId())).toList();
        if (!stale.isEmpty()) {
            bindings.deleteAll(stale);
            bindings.flush();
        }
        Set<UUID> present = current.stream().map(ResourceAsset::getAssetId).collect(java.util.stream.Collectors.toSet());
        int next = bindings.findMaxSortOrder(projectId, kind, resourceId) + 1;
        for (UUID assetId : embedded.stream().sorted().toList()) {
            if (!present.contains(assetId)) {
                bindings.save(ResourceAsset.of(projectId, kind, resourceId, assetId, AssetUsage.INLINE, null, next++));
            }
        }
        bindings.flush();
    }
}
