package eu.uniche.iguide.poi.infrastructure;

import eu.uniche.iguide.poi.domain.PointOfInterest;
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
public interface PointOfInterestRepository
        extends JpaRepository<PointOfInterest, UUID>, JpaSpecificationExecutor<PointOfInterest> {

    Optional<PointOfInterest> findByProjectIdAndId(UUID projectId, UUID id);

    List<PointOfInterest> findByProjectId(UUID projectId);

    List<PointOfInterest> findByProjectIdAndIdIn(UUID projectId, Collection<UUID> ids);

    boolean existsByProjectIdAndSlug(UUID projectId, String slug);

    boolean existsByProjectIdAndSlugAndIdNot(UUID projectId, String slug, UUID id);

    boolean existsByProjectIdAndCode(UUID projectId, String code);

    boolean existsByProjectIdAndCodeAndIdNot(UUID projectId, String code, UUID id);

    long countByProjectIdAndStatusNot(UUID projectId, ResourceStatus status);

    @Query("select coalesce(max(p.sortOrder), 0) from PointOfInterest p where p.projectId = :projectId")
    int findMaxSortOrder(@Param("projectId") UUID projectId);

    /** Sets the order without touching {@code version}: reordering is not an edit of the POI. */
    @Modifying
    @Query("update PointOfInterest p set p.sortOrder = :sortOrder where p.projectId = :projectId and p.id = :id")
    int updateSortOrder(@Param("projectId") UUID projectId, @Param("id") UUID id, @Param("sortOrder") int sortOrder);
}
