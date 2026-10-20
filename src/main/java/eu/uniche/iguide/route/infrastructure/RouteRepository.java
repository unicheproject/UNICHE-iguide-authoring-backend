package eu.uniche.iguide.route.infrastructure;

import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.route.domain.Route;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query takes the Project explicitly ({@code 06} section 2). */
public interface RouteRepository extends JpaRepository<Route, UUID>, JpaSpecificationExecutor<Route> {

    Optional<Route> findByProjectIdAndId(UUID projectId, UUID id);

    List<Route> findByProjectIdAndIdIn(UUID projectId, Collection<UUID> ids);

    List<Route> findByProjectId(UUID projectId);

    boolean existsByProjectIdAndSlug(UUID projectId, String slug);

    boolean existsByProjectIdAndSlugAndIdNot(UUID projectId, String slug, UUID id);

    long countByProjectIdAndStatusNot(UUID projectId, ResourceStatus status);

    @Query("select coalesce(max(r.sortOrder), 0) from Route r where r.projectId = :projectId")
    int findMaxSortOrder(@Param("projectId") UUID projectId);

    /** Sets the order without touching {@code version}: reordering is not an edit of the Route. */
    @Modifying
    @Query("update Route r set r.sortOrder = :sortOrder where r.projectId = :projectId and r.id = :id")
    int updateSortOrder(@Param("projectId") UUID projectId, @Param("id") UUID id, @Param("sortOrder") int sortOrder);

    /** Non-archived Routes with a stop on the Point of Interest. */
    @Query("""
            select r from Route r
            where r.projectId = :projectId and r.status <> eu.uniche.iguide.resource.domain.ResourceStatus.ARCHIVED
              and exists (select s.id from RouteStop s
                          where s.projectId = :projectId and s.routeId = r.id and s.poiId = :poiId)
            """)
    List<Route> findUsingPoi(@Param("projectId") UUID projectId, @Param("poiId") UUID poiId);
}
