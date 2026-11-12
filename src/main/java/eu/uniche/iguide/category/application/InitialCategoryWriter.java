package eu.uniche.iguide.category.application;

import eu.uniche.iguide.category.domain.Category;
import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.category.domain.CategoryTranslation;
import eu.uniche.iguide.category.infrastructure.CategoryRepository;
import eu.uniche.iguide.category.infrastructure.CategoryTranslationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Materializes a Template's {@code initialCategories} at Project initialization ({@code 04} section 6, section 8
 * step 8): each one becomes a DRAFT Category with its translations in the Project's enabled languages, last in the
 * Project's order. A code the Project already uses is skipped, so a repeated initialization never duplicates a row
 * (DECISIONS.md D-040). Runs inside the caller's transaction, after the access check.
 */
@Component
public class InitialCategoryWriter {

    /** One Category as a Template defines it; {@code names} is keyed by language code. */
    public record InitialCategory(String code, CategoryScope scope, String icon, Map<String, String> names,
                                  Map<String, String> descriptions) {
    }

    private final CategoryRepository categories;
    private final CategoryTranslationRepository translations;

    public InitialCategoryWriter(CategoryRepository categories, CategoryTranslationRepository translations) {
        this.categories = categories;
        this.translations = translations;
    }

    /** @return how many Categories were created */
    public int materialize(UUID projectId, List<InitialCategory> initial, Set<String> enabledLanguages,
                           String subject, Instant now) {
        int created = 0;
        int sortOrder = categories.findMaxSortOrder(projectId);
        for (InitialCategory definition : initial) {
            if (categories.existsByProjectIdAndCode(projectId, definition.code())) {
                continue;
            }
            Category category = Category.draft(projectId, null, definition.scope(), definition.code(), ++sortOrder,
                    subject, now);
            category.edit(definition.code(), definition.scope(), null, definition.icon(), subject, now);
            category = categories.save(category);
            for (Map.Entry<String, String> name : definition.names().entrySet()) {
                if (!enabledLanguages.contains(name.getKey())) {
                    continue;
                }
                CategoryTranslation translation = CategoryTranslation.of(projectId, category.getId(), name.getKey());
                translation.write(name.getValue(), definition.descriptions() == null ? null
                        : definition.descriptions().get(name.getKey()));
                translations.save(translation);
            }
            created++;
        }
        return created;
    }
}
