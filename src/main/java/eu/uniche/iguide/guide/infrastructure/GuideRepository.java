package eu.uniche.iguide.guide.infrastructure;

import eu.uniche.iguide.guide.domain.Guide;
import eu.uniche.iguide.guide.domain.GuideTargetType;
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
public interface GuideRepository extends JpaRepository<Guide, UUID>, JpaSpecificationExecutor<Guide> {

    Optional<Guide> findByProjectIdAndId(UUID projectId, UUID id);

    List<Guide> findByProjectIdAndIdIn(UUID projectId, Collection<UUID> ids);

    List<Guide> findByProjectId(UUID projectId);

    boolean existsByProjectIdAndSlug(UUID projectId, String slug);

    boolean existsByProjectIdAndSlugAndIdNot(UUID projectId, String slug, UUID id);

    long countByProjectIdAndStatusNot(UUID projectId, ResourceStatus status);

    @Query("select coalesce(max(g.sortOrder), 0) from Guide g where g.projectId = :projectId")
    int findMaxSortOrder(@Param("projectId") UUID projectId);

    /** Sets the order without touching {@code version}: reordering is not an edit of the Guide. */
    @Modifying
    @Query("update Guide g set g.sortOrder = :sortOrder where g.projectId = :projectId and g.id = :id")
    int updateSortOrder(@Param("projectId") UUID projectId, @Param("id") UUID id, @Param("sortOrder") int sortOrder);

    /** Non-archived Guides with a REFERENCE block on the Resource. */
    @Query("""
            select g from Guide g
            where g.projectId = :projectId and g.status <> eu.uniche.iguide.resource.domain.ResourceStatus.ARCHIVED
              and exists (select b.id from GuideBlock b
                          where b.projectId = :projectId and b.guideId = g.id
                            and b.resourceType = :resourceType and b.resourceId = :resourceId)
            order by g.sortOrder
            """)
    List<Guide> findReferencing(@Param("projectId") UUID projectId, @Param("resourceType") GuideTargetType resourceType,
                                @Param("resourceId") UUID resourceId);
}
