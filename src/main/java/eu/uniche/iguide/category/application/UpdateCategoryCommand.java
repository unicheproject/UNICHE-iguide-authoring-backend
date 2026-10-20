package eu.uniche.iguide.category.application;

import eu.uniche.iguide.category.domain.CategoryScope;
import java.util.Map;
import java.util.UUID;

/** The full editable state of a DRAFT Category; a language absent from {@code translations} keeps its text. */
public record UpdateCategoryCommand(Long version, String code, CategoryScope scope, UUID parentCategoryId, String icon,
                                    Map<String, CategoryTranslationInput> translations) {
}
