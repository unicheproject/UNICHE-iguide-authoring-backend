package eu.uniche.iguide.event.infrastructure;

import eu.uniche.iguide.event.domain.Event;
import eu.uniche.iguide.resource.domain.ResourceStatus;
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
public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    Optional<Event> findByProjectIdAndId(UUID projectId, UUID id);

    List<Event> findByProjectIdAndIdIn(UUID projectId, Collection<UUID> ids);

    List<Event> findByProjectId(UUID projectId);

    boolean existsByProjectIdAndSlug(UUID projectId, String slug);

    boolean existsByProjectIdAndSlugAndIdNot(UUID projectId, String slug, UUID id);

    long countByProjectIdAndStatusNot(UUID projectId, ResourceStatus status);

    List<Event> findByProjectIdAndPlaceIdAndStatusNotOrderBySortOrderAsc(UUID projectId, UUID placeId, ResourceStatus status);

    List<Event> findByProjectIdAndPoiIdAndStatusNotOrderBySortOrderAsc(UUID projectId, UUID poiId, ResourceStatus status);

    List<Event> findByProjectIdAndRouteIdAndStatusNotOrderBySortOrderAsc(UUID projectId, UUID routeId, ResourceStatus status);

    @Query("select coalesce(max(e.sortOrder), 0) from Event e where e.projectId = :projectId")
    int findMaxSortOrder(@Param("projectId") UUID projectId);

    /** Sets the order without touching {@code version}: reordering is not an edit of the Event. */
    @Modifying
    @Query("update Event e set e.sortOrder = :sortOrder where e.projectId = :projectId and e.id = :id")
    int updateSortOrder(@Param("projectId") UUID projectId, @Param("id") UUID id, @Param("sortOrder") int sortOrder);
}
