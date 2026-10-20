package eu.uniche.iguide.category.application;

import eu.uniche.iguide.category.infrastructure.CategoryRepository;
import eu.uniche.iguide.category.infrastructure.CategoryTranslationRepository;
import eu.uniche.iguide.language.application.TitledResourceCounter;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Categories' share of the translation completeness counts; a Category's title is its name. */
@Component
public class CategoryTitleCounter implements TitledResourceCounter {

    private final CategoryRepository categories;
    private final CategoryTranslationRepository translations;

    public CategoryTitleCounter(CategoryRepository categories, CategoryTranslationRepository translations) {
        this.categories = categories;
        this.translations = translations;
    }

    @Override
    public long countResources(UUID projectId) {
        return categories.countByProjectIdAndStatusNot(projectId, ResourceStatus.ARCHIVED);
    }

    @Override
    public long countTitled(UUID projectId, String languageCode) {
        return translations.countNamed(projectId, languageCode, ResourceStatus.ARCHIVED);
    }
}
