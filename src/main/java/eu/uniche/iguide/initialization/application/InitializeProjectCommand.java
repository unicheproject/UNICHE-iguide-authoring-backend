package eu.uniche.iguide.initialization.application;

import eu.uniche.iguide.language.domain.TextDirection;
import eu.uniche.iguide.surface.domain.Surface;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code POST .../initialize} ({@code 04} section 8, {@code 07} section 8). Every member but {@code templateId} is
 * optional: {@code stylePreset} defaults to the Template's {@code defaultStylePreset}, {@code languages} to its
 * {@code defaultLanguages}, {@code defaultLanguage} to the first language, {@code surfaces} to every supported
 * surface, and {@code featureConfiguration} to every declared capability enabled.
 */
public record InitializeProjectCommand(UUID templateId,
                                       String stylePreset,
                                       List<RequestedLanguage> languages,
                                       String defaultLanguage,
                                       List<Surface> surfaces,
                                       Map<String, Boolean> featureConfiguration) {

    /** A language by code; the name and direction default to the known ones or the Project's existing row. */
    public record RequestedLanguage(String languageCode, String displayName, TextDirection direction) {
    }
}
