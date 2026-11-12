package eu.uniche.iguide.template.application;

import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.surface.domain.Surface;
import eu.uniche.iguide.template.domain.Capability;
import eu.uniche.iguide.template.domain.CornerRadius;
import eu.uniche.iguide.template.domain.Density;
import eu.uniche.iguide.template.domain.ThemeFont;
import java.util.List;
import java.util.Map;

/**
 * The typed form of a Template's {@code definition} ({@code 04} section 6), so no raw map reaches a controller.
 * Read strictly from the file: an unknown key or value fails startup.
 */
public record TemplateDefinition(List<Surface> supportedSurfaces,
                                 List<Capability> capabilities,
                                 List<String> defaultLanguages,
                                 List<InitialCategoryDefinition> initialCategories,
                                 List<String> allowedComponentTypes,
                                 ThemeValueSets themeValues,
                                 StyleValues defaultTheme,
                                 List<String> stylePresets,
                                 String defaultStylePreset,
                                 Map<String, List<String>> shellSchema,
                                 Map<String, String> defaultShell,
                                 List<Map<String, Object>> initialPages,
                                 List<Map<String, Object>> initialNavigations,
                                 Map<Surface, Map<String, Object>> runtimeDefaults) {

    /** The allowed values of the typed theme properties chosen from a set; colors are free within contrast. */
    public record ThemeValueSets(List<ThemeFont> headingFont,
                                 List<ThemeFont> bodyFont,
                                 List<CornerRadius> cornerRadius,
                                 List<Density> density) {

        public boolean allows(String field, String value) {
            List<? extends Enum<?>> allowed = switch (field) {
                case "headingFont" -> headingFont;
                case "bodyFont" -> bodyFont;
                case "cornerRadius" -> cornerRadius;
                case "density" -> density;
                default -> List.of();
            };
            return value != null && allowed != null && allowed.stream().anyMatch(v -> v.name().equals(value));
        }
    }

    /** One Category to materialize; {@code name} and {@code description} are keyed by language code. */
    public record InitialCategoryDefinition(String code,
                                            CategoryScope scope,
                                            String icon,
                                            Map<String, String> name,
                                            Map<String, String> description) {
    }

    public List<String> defaultLanguagesOrEmpty() {
        return defaultLanguages == null ? List.of() : defaultLanguages;
    }

    public List<InitialCategoryDefinition> initialCategoriesOrEmpty() {
        return initialCategories == null ? List.of() : initialCategories;
    }

    public Map<Surface, Map<String, Object>> runtimeDefaultsOrEmpty() {
        return runtimeDefaults == null ? Map.of() : runtimeDefaults;
    }
}
