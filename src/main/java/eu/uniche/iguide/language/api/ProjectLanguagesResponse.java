package eu.uniche.iguide.language.api;

import eu.uniche.iguide.language.application.ProjectLanguagesView;
import eu.uniche.iguide.language.domain.TextDirection;
import java.util.List;

/**
 * The enabled languages of a Project in display order.
 *
 * @param resourceCount the non-archived translated Resources, against which each
 *                      {@code titledResourceCount} reads as translation completeness
 */
public record ProjectLanguagesResponse(List<Language> languages, long resourceCount) {

    public record Language(String languageCode, String displayName, TextDirection direction, boolean isDefault,
                           int sortOrder, long titledResourceCount) {
    }

    public static ProjectLanguagesResponse from(ProjectLanguagesView view) {
        return new ProjectLanguagesResponse(view.languages().stream()
                .map(l -> new Language(l.getLanguageCode(), l.getDisplayName(), l.getDirection(),
                        l.isDefaultLanguage(), l.getSortOrder(),
                        view.titledResourceCounts().getOrDefault(l.getLanguageCode(), 0L)))
                .toList(), view.resourceCount());
    }
}
