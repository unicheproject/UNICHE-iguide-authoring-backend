package eu.uniche.iguide.category.api;

import eu.uniche.iguide.category.application.CategoryTranslationInput;
import eu.uniche.iguide.category.domain.CategoryScope;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Request bodies of the Category endpoints. */
public final class CategoryRequests {

    private CategoryRequests() {
    }

    /** {@code name} is in the default language; {@code code} is generated from it when absent. */
    public record Create(String name, String code, CategoryScope scope, UUID parentCategoryId) {
    }

    public record Update(@NotNull Long version, String code, CategoryScope scope, UUID parentCategoryId, String icon,
                         Map<String, CategoryTranslationBody> translations) {

        Map<String, CategoryTranslationInput> translationInputs() {
            return inputs(translations);
        }
    }

    static Map<String, CategoryTranslationInput> inputs(Map<String, CategoryTranslationBody> translations) {
        return translations == null ? Map.of() : translations.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, e -> new CategoryTranslationInput(e.getValue().name(), e.getValue().description())));
    }

    public record CategoryTranslationBody(String name, String description) {
    }

    /** Puts a Point of Interest or Route in a Category. */
    public record Bind(UUID categoryId) {
    }
}
