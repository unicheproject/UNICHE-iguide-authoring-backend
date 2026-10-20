package eu.uniche.iguide.language.application;

import eu.uniche.iguide.language.domain.ProjectLanguage;
import java.util.List;
import java.util.Map;

/**
 * The enabled languages, with how many non-archived Resources have a title in each, for the
 * translation matrix and for the confirmation before removing a language.
 */
public record ProjectLanguagesView(List<ProjectLanguage> languages,
                                   long resourceCount,
                                   Map<String, Long> titledResourceCounts) {
}
