package eu.uniche.iguide.place.infrastructure;

import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query takes the Project explicitly ({@code 06} section 2). */
public interface PlaceRepository extends JpaRepository<Place, UUID>, JpaSpecificationExecutor<Place> {

    Optional<Place> findByProjectIdAndId(UUID projectId, UUID id);

    List<Place> findByProjectId(UUID projectId);

    boolean existsByProjectIdAndSlug(UUID projectId, String slug);

    boolean existsByProjectIdAndSlugAndIdNot(UUID projectId, String slug, UUID id);

    long countByProjectIdAndStatusNot(UUID projectId, ResourceStatus status);

    @Query("select coalesce(max(p.sortOrder), 0) from Place p where p.projectId = :projectId")
    int findMaxSortOrder(@Param("projectId") UUID projectId);

    /** Sets the order without touching {@code version}: reordering is not an edit of the Place. */
    @Modifying
    @Query("update Place p set p.sortOrder = :sortOrder where p.projectId = :projectId and p.id = :id")
    int updateSortOrder(@Param("projectId") UUID projectId, @Param("id") UUID id, @Param("sortOrder") int sortOrder);
}
