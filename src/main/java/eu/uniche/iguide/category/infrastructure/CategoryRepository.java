package eu.uniche.iguide.category.infrastructure;

import eu.uniche.iguide.category.domain.Category;
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
public interface CategoryRepository extends JpaRepository<Category, UUID>, JpaSpecificationExecutor<Category> {

    Optional<Category> findByProjectIdAndId(UUID projectId, UUID id);

    List<Category> findByProjectIdAndIdIn(UUID projectId, Collection<UUID> ids);

    List<Category> findByProjectId(UUID projectId);

    List<Category> findByProjectIdAndParentCategoryId(UUID projectId, UUID parentCategoryId);

    List<Category> findByProjectIdAndParentCategoryIdAndStatusNotOrderBySortOrderAsc(UUID projectId, UUID parentCategoryId,
                                                                                   ResourceStatus status);

    boolean existsByProjectIdAndCode(UUID projectId, String code);

    boolean existsByProjectIdAndCodeAndIdNot(UUID projectId, String code, UUID id);

    long countByProjectIdAndStatusNot(UUID projectId, ResourceStatus status);

    @Query("select coalesce(max(c.sortOrder), 0) from Category c where c.projectId = :projectId")
    int findMaxSortOrder(@Param("projectId") UUID projectId);

    /** Sets the order without touching {@code version}: reordering is not an edit of the Category. */
    @Modifying
    @Query("update Category c set c.sortOrder = :sortOrder where c.projectId = :projectId and c.id = :id")
    int updateSortOrder(@Param("projectId") UUID projectId, @Param("id") UUID id, @Param("sortOrder") int sortOrder);
}
