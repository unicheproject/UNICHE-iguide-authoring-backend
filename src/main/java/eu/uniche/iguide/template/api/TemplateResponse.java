package eu.uniche.iguide.template.api;

import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.language.application.KnownLanguages;
import eu.uniche.iguide.language.domain.TextDirection;
import eu.uniche.iguide.platform.web.LocalizedText;
import eu.uniche.iguide.surface.domain.Surface;
import eu.uniche.iguide.template.application.StyleValues;
import eu.uniche.iguide.template.application.TemplateDefinition;
import eu.uniche.iguide.template.application.TemplateView;
import eu.uniche.iguide.template.domain.ApplicationTemplate;
import eu.uniche.iguide.template.domain.Capability;
import eu.uniche.iguide.template.domain.StylePreset;
import eu.uniche.iguide.template.domain.TemplateStatus;
import eu.uniche.iguide.template.domain.ThemeFont;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One Template version as the CMS shows it when a Project is initialized ({@code 12} section 4 Create a Project)
 * and in App settings ({@code 04} sections 4, 6, 9). Names and descriptions are in the caller's
 * {@code Accept-Language}, falling back to {@code en}.
 *
 * @param fonts        the CSS family of every font key the Template allows, so a tile can be drawn in its font
 * @param stylePresets the offered presets with every value a swatch and preview need; {@code suggested} marks
 *                     {@code defaultStylePreset}
 */
public record TemplateResponse(UUID id,
                               String key,
                               int version,
                               TemplateStatus status,
                               String name,
                               String description,
                               List<Surface> supportedSurfaces,
                               List<Capability> capabilities,
                               List<DefaultLanguage> defaultLanguages,
                               List<String> allowedComponentTypes,
                               TemplateDefinition.ThemeValueSets themeValues,
                               List<Font> fonts,
                               StyleValues defaultTheme,
                               List<Preset> stylePresets,
                               String defaultStylePreset,
                               Map<String, List<String>> shellSchema,
                               Map<String, String> defaultShell,
                               List<InitialCategory> initialCategories) {

    /** A language the Template proposes; the first is the default ({@code 04} section 6). */
    public record DefaultLanguage(String languageCode, String displayName, TextDirection direction,
                                  boolean isDefault) {
    }

    public record Font(String key, String family) {
    }

    public record Preset(String key, String name, String description, boolean suggested, String headingFont,
                         String bodyFont, String primaryColor, String secondaryColor, String backgroundColor,
                         String textColor, String cornerRadius, String density) {
    }

    /** A Category initialization creates, with its name in the caller's language. */
    public record InitialCategory(String code, CategoryScope scope, String icon, String name) {
    }

    public record TemplatesResponse(List<TemplateResponse> templates) {
    }

    public static TemplateResponse from(TemplateView view) {
        ApplicationTemplate template = view.template();
        TemplateDefinition d = view.definition();
        List<DefaultLanguage> languages = new ArrayList<>();
        List<String> codes = d.defaultLanguagesOrEmpty();
        for (int i = 0; i < codes.size(); i++) {
            String code = codes.get(i);
            KnownLanguages.Known known = KnownLanguages.find(code).orElse(null);
            languages.add(new DefaultLanguage(code, known == null ? code : known.displayName(),
                    known == null ? TextDirection.LTR : known.direction(), i == 0));
        }
        Set<ThemeFont> fonts = EnumSet.noneOf(ThemeFont.class);
        fonts.addAll(d.themeValues().headingFont());
        fonts.addAll(d.themeValues().bodyFont());
        return new TemplateResponse(template.getId(), template.getKey(), template.getVersion(), template.getStatus(),
                LocalizedText.pick(template.getName()), LocalizedText.pick(template.getDescription()),
                d.supportedSurfaces(), d.capabilities(), languages,
                d.allowedComponentTypes() == null ? List.of() : d.allowedComponentTypes(), d.themeValues(),
                fonts.stream().map(font -> new Font(font.name(), font.family())).toList(), d.defaultTheme(),
                view.presets().stream().map(preset -> preset(preset, d.defaultStylePreset())).toList(),
                d.defaultStylePreset(), d.shellSchema(), d.defaultShell(),
                d.initialCategoriesOrEmpty().stream().map(c -> new InitialCategory(c.code(), c.scope(), c.icon(),
                        LocalizedText.pick(c.name()))).toList());
    }

    private static Preset preset(StylePreset preset, String suggested) {
        StyleValues v = StyleValues.fromMap(preset.getValues());
        return new Preset(preset.getKey(), LocalizedText.pick(preset.getName()),
                LocalizedText.pick(preset.getDescription()), preset.getKey().equals(suggested), v.headingFont(),
                v.bodyFont(), v.primaryColor(), v.secondaryColor(), v.backgroundColor(), v.textColor(),
                v.cornerRadius(), v.density());
    }
}
