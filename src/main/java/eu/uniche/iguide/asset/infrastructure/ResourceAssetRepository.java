package eu.uniche.iguide.asset.infrastructure;

import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.asset.domain.AssetUsage;
import eu.uniche.iguide.asset.domain.ResourceAsset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query takes the Project explicitly. */
public interface ResourceAssetRepository extends JpaRepository<ResourceAsset, UUID> {

    List<ResourceAsset> findByProjectIdAndResourceTypeAndResourceIdOrderBySortOrderAsc(
            UUID projectId, AssetResourceType resourceType, UUID resourceId);

    List<ResourceAsset> findByProjectIdAndResourceTypeAndResourceIdInOrderBySortOrderAsc(
            UUID projectId, AssetResourceType resourceType, Collection<UUID> resourceIds);

    Optional<ResourceAsset> findByProjectIdAndResourceTypeAndResourceIdAndId(
            UUID projectId, AssetResourceType resourceType, UUID resourceId, UUID id);

    List<ResourceAsset> findByProjectIdAndResourceTypeAndResourceIdAndUsage(
            UUID projectId, AssetResourceType resourceType, UUID resourceId, AssetUsage usage);

    List<ResourceAsset> findByProjectIdAndAssetId(UUID projectId, UUID assetId);

    List<ResourceAsset> findByProjectIdAndAssetIdIn(UUID projectId, Collection<UUID> assetIds);

    @Query("""
            select coalesce(max(b.sortOrder), -1) from ResourceAsset b
            where b.projectId = :projectId and b.resourceType = :resourceType and b.resourceId = :resourceId
            """)
    int findMaxSortOrder(@Param("projectId") UUID projectId, @Param("resourceType") AssetResourceType resourceType,
                         @Param("resourceId") UUID resourceId);
}
