package eu.uniche.iguide.language.api;

import eu.uniche.iguide.language.domain.TextDirection;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * The complete list of enabled languages, in display order. A language left out is disabled.
 */
public record UpdateProjectLanguagesRequest(@NotNull List<LanguageRequest> languages) {

    public record LanguageRequest(String languageCode, String displayName, TextDirection direction,
                                  boolean isDefault) {
    }
}
