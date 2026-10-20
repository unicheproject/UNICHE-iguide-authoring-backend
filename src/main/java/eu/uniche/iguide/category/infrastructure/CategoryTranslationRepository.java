package eu.uniche.iguide.category.infrastructure;

import eu.uniche.iguide.category.domain.CategoryTranslation;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query takes the Project explicitly. */
public interface CategoryTranslationRepository extends JpaRepository<CategoryTranslation, UUID> {

    List<CategoryTranslation> findByProjectIdAndCategoryId(UUID projectId, UUID categoryId);

    List<CategoryTranslation> findByProjectIdAndCategoryIdIn(UUID projectId, Collection<UUID> categoryIds);

    @Query("""
            select count(t) from CategoryTranslation t, Category c
            where t.projectId = :projectId and c.projectId = :projectId and c.id = t.categoryId
              and t.languageCode = :languageCode and c.status <> :excluded
              and t.name is not null and trim(t.name) <> ''
            """)
    long countNamed(@Param("projectId") UUID projectId, @Param("languageCode") String languageCode,
                    @Param("excluded") ResourceStatus excluded);
}
