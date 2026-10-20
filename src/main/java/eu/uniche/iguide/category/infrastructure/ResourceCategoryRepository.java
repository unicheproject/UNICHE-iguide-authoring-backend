package eu.uniche.iguide.category.infrastructure;

import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.category.domain.ResourceCategory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query takes the Project explicitly. */
public interface ResourceCategoryRepository extends JpaRepository<ResourceCategory, UUID> {

    List<ResourceCategory> findByProjectIdAndResourceTypeAndResourceId(UUID projectId, CategoryScope resourceType,
                                                                       UUID resourceId);

    List<ResourceCategory> findByProjectIdAndResourceTypeAndResourceIdIn(UUID projectId, CategoryScope resourceType,
                                                                         Collection<UUID> resourceIds);

    Optional<ResourceCategory> findByProjectIdAndResourceTypeAndResourceIdAndId(UUID projectId,
                                                                                CategoryScope resourceType,
                                                                                UUID resourceId, UUID id);

    List<ResourceCategory> findByProjectIdAndCategoryId(UUID projectId, UUID categoryId);

    List<ResourceCategory> findByProjectIdAndCategoryIdIn(UUID projectId, Collection<UUID> categoryIds);

    boolean existsByProjectIdAndCategoryId(UUID projectId, UUID categoryId);
}
