package eu.uniche.iguide.floorplan.infrastructure;

import eu.uniche.iguide.floorplan.domain.FloorPlan;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query takes the Project explicitly. */
public interface FloorPlanRepository extends JpaRepository<FloorPlan, UUID>, JpaSpecificationExecutor<FloorPlan> {

    Optional<FloorPlan> findByProjectIdAndId(UUID projectId, UUID id);

    List<FloorPlan> findByProjectId(UUID projectId);

    List<FloorPlan> findByProjectIdAndPlaceId(UUID projectId, UUID placeId);

    List<FloorPlan> findByProjectIdAndAssetId(UUID projectId, UUID assetId);

    List<FloorPlan> findByProjectIdAndAssetIdIn(UUID projectId, Collection<UUID> assetIds);

    @Query("select coalesce(max(f.sortOrder), 0) from FloorPlan f where f.projectId = :projectId")
    int findMaxSortOrder(@Param("projectId") UUID projectId);

    @Modifying
    @Query("update FloorPlan f set f.sortOrder = :sortOrder where f.projectId = :projectId and f.id = :id")
    int updateSortOrder(@Param("projectId") UUID projectId, @Param("id") UUID id, @Param("sortOrder") int sortOrder);

    /** Non-archived plans that position the Resource. */
    @Query("""
            select f from FloorPlan f
            where f.projectId = :projectId and f.status <> eu.uniche.iguide.resource.domain.ResourceStatus.ARCHIVED
              and exists (select p.id from FloorPlanPosition p
                          where p.projectId = :projectId and p.floorPlanId = f.id
                            and p.resourceType = :resourceType and p.resourceId = :resourceId)
            """)
    List<FloorPlan> findPositioning(@Param("projectId") UUID projectId, @Param("resourceType") String resourceType,
                                    @Param("resourceId") UUID resourceId);
}
