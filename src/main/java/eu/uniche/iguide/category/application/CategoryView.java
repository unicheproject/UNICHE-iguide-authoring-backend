package eu.uniche.iguide.category.application;

import eu.uniche.iguide.category.domain.Category;
import eu.uniche.iguide.category.domain.CategoryTranslation;
import java.util.List;

/** A Category with its translations and the number of Points of Interest or Routes bound to it. */
public record CategoryView(Category category, List<CategoryTranslation> translations, long resourceCount) {
}
