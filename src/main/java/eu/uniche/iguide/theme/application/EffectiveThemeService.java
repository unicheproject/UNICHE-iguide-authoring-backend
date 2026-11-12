package eu.uniche.iguide.theme.application;

import eu.uniche.iguide.template.application.Colors;
import eu.uniche.iguide.template.application.StyleValues;
import eu.uniche.iguide.template.application.TemplateDefinition;
import eu.uniche.iguide.template.application.TemplateLookup;
import eu.uniche.iguide.theme.domain.ProjectTheme;
import eu.uniche.iguide.theme.infrastructure.ProjectThemeRepository;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Effective theme resolution ({@code 04} section 10): the applied Template's {@code defaultTheme} overridden by the
 * Project's typed values. A preset is not a layer; the Template's data is never changed. Callers have passed the
 * Project access check.
 */
@Component
public class EffectiveThemeService {

    private final ProjectThemeRepository themes;
    private final TemplateLookup templates;

    public EffectiveThemeService(ProjectThemeRepository themes, TemplateLookup templates) {
        this.themes = themes;
        this.templates = templates;
    }

    /** Empty while the Project is not initialized. */
    public Optional<ThemeView> resolve(UUID projectId) {
        return templates.applied(projectId).flatMap(applied -> themes.findById(projectId)
                .map(theme -> view(theme, applied.definition())));
    }

    public ThemeView view(ProjectTheme theme, TemplateDefinition definition) {
        StyleValues effective = theme.values().over(definition.defaultTheme());
        boolean modified = templates.preset(theme.getStylePresetKey())
                .map(preset -> !normalized(StyleValues.fromMap(preset.getValues())).equals(normalized(effective)))
                .orElse(false);
        return new ThemeView(theme, effective, modified, contrast(effective));
    }

    static ThemeView.Contrast contrast(StyleValues effective) {
        String background = effective.backgroundColor();
        return new ThemeView.Contrast(ratio(effective.textColor(), background),
                ratio(effective.primaryColor(), background), ratio(effective.secondaryColor(), background),
                Colors.contrastFailures(effective).isEmpty());
    }

    private static Double ratio(String color, String background) {
        return Colors.valid(color) && Colors.valid(background) ? Colors.display(Colors.contrast(color, background))
                : null;
    }

    private static Map<String, String> normalized(StyleValues values) {
        return values.asMap().entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> Objects.requireNonNull(entry.getValue()).toUpperCase(Locale.ROOT)));
    }
}
