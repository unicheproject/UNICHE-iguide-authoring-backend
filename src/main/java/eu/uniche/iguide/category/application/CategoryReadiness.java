package eu.uniche.iguide.category.application;

import eu.uniche.iguide.category.domain.Category;
import eu.uniche.iguide.category.domain.CategoryTranslation;
import eu.uniche.iguide.category.infrastructure.CategoryRepository;
import eu.uniche.iguide.category.infrastructure.CategoryTranslationRepository;
import eu.uniche.iguide.readiness.application.ReadinessCheck;
import eu.uniche.iguide.readiness.application.ReadinessContext;
import eu.uniche.iguide.readiness.application.ReadinessReport;
import eu.uniche.iguide.readiness.application.Texts;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Readiness of ACTIVE Categories ({@code 03} sections 1 and 10; DECISIONS.md D-033): the name in the default language
 * and translations of the name and description.
 */
@Component
public class CategoryReadiness implements ReadinessCheck {

    static final List<String> FIELDS = List.of("name", "description");

    private final CategoryRepository categories;
    private final CategoryTranslationRepository translations;

    public CategoryReadiness(CategoryRepository categories, CategoryTranslationRepository translations) {
        this.categories = categories;
        this.translations = translations;
    }

    @Override
    public void check(ReadinessContext context, ReadinessReport report) {
        List<UUID> active = categories.findByProjectId(context.projectId()).stream()
                .filter(c -> c.getStatus() == ResourceStatus.ACTIVE).map(Category::getId).toList();
        if (active.isEmpty()) {
            return;
        }
        Map<UUID, Map<String, Map<String, String>>> texts = Texts.byOwner(
                translations.findByProjectIdAndCategoryIdIn(context.projectId(), active),
                CategoryTranslation::getCategoryId, CategoryTranslation::getLanguageCode,
                t -> Texts.of("name", t.getName(), "description", t.getDescription()));
        for (UUID id : active) {
            report.texts(context, "CATEGORY", id, context.index().title("CATEGORY", id),
                    texts.getOrDefault(id, Map.of()), FIELDS, "name", List.of());
        }
    }
}
