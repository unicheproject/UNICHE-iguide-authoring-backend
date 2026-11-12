package eu.uniche.iguide.template.application;

import eu.uniche.iguide.category.application.CategoryRules;
import eu.uniche.iguide.language.application.KnownLanguages;
import eu.uniche.iguide.language.application.ProjectLanguagesWriter;
import eu.uniche.iguide.surface.application.UpdateProjectSurfacesUseCase;
import eu.uniche.iguide.surface.domain.Surface;
import eu.uniche.iguide.template.application.TemplateDefinition.InitialCategoryDefinition;
import eu.uniche.iguide.template.domain.CornerRadius;
import eu.uniche.iguide.template.domain.Density;
import eu.uniche.iguide.template.domain.ShellKeys;
import eu.uniche.iguide.template.domain.ThemeFont;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Template validation of the files themselves ({@code 04} sections 6, 9, 11, 15): every rule a Project later
 * relies on is checked once, at startup, so initialization never meets an inconsistent Template.
 */
public final class TemplateFileValidator {

    private static final Pattern KEY = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private TemplateFileValidator() {
    }

    /** @throws TemplateSeedingException listing every problem */
    public static void validate(List<TemplateFile> templates, List<StylePresetFile> presets) {
        List<String> problems = new ArrayList<>();
        Map<String, StylePresetFile> presetsByKey = new HashMap<>();
        for (StylePresetFile preset : presets) {
            String where = "style preset " + preset.filename();
            if (preset.key() == null || !KEY.matcher(preset.key()).matches()) {
                problems.add(where + ": key must be lowercase words joined by hyphens");
            } else if (!preset.filename().equals(preset.key() + ".json")) {
                problems.add(where + ": file must be named " + preset.key() + ".json");
            } else if (presetsByKey.put(preset.key(), preset) != null) {
                problems.add(where + ": key used twice");
            }
            texts(where, preset.name(), "name", problems);
            texts(where, preset.description(), "description", problems);
            completeStyle(where + " values", preset.values(), problems);
        }

        Set<String> seen = new HashSet<>();
        for (TemplateFile template : templates) {
            String where = "template " + template.filename();
            if (template.key() == null || !KEY.matcher(template.key()).matches()) {
                problems.add(where + ": key must be lowercase words joined by hyphens");
                continue;
            }
            if (template.version() < 1) {
                problems.add(where + ": version must be a positive integer");
                continue;
            }
            if (!template.filename().equals(template.key() + ".v" + template.version() + ".json")) {
                problems.add(where + ": file must be named " + template.key() + ".v" + template.version() + ".json");
            }
            if (!seen.add(template.key() + ":" + template.version())) {
                problems.add(where + ": key and version used twice");
            }
            texts(where, template.name(), "name", problems);
            texts(where, template.description(), "description", problems);
            definition(where, template.definition(), presetsByKey, problems);
        }
        if (!problems.isEmpty()) {
            throw new TemplateSeedingException("Template files are invalid:\n - " + String.join("\n - ", problems),
                    null);
        }
    }

    private static void definition(String where, TemplateDefinition d, Map<String, StylePresetFile> presets,
                                   List<String> problems) {
        if (d.supportedSurfaces() == null || d.supportedSurfaces().isEmpty()) {
            problems.add(where + ": supportedSurfaces must name at least one surface");
        } else if (new HashSet<>(d.supportedSurfaces()).size() != d.supportedSurfaces().size()) {
            problems.add(where + ": supportedSurfaces lists a surface twice");
        }
        if (d.capabilities() == null || d.capabilities().isEmpty()) {
            problems.add(where + ": capabilities must name at least one capability");
        } else if (new HashSet<>(d.capabilities()).size() != d.capabilities().size()) {
            problems.add(where + ": capabilities lists a capability twice");
        }
        Set<String> languages = new HashSet<>();
        for (String code : d.defaultLanguagesOrEmpty()) {
            if (code == null || !ProjectLanguagesWriter.CODE.matcher(code).matches()
                    || KnownLanguages.find(code).isEmpty()) {
                problems.add(where + ": defaultLanguages names a language without a known name: " + code);
            } else if (!languages.add(code)) {
                problems.add(where + ": defaultLanguages lists " + code + " twice");
            }
        }
        categories(where, d.initialCategoriesOrEmpty(), problems);
        if (d.initialPages() != null && !d.initialPages().isEmpty()
                || d.initialNavigations() != null && !d.initialNavigations().isEmpty()) {
            // Pages and Navigation arrive with Milestone 6 (GAPS.md G-038); until then nothing could materialize them.
            problems.add(where + ": initialPages and initialNavigations must stay empty until Pages exist");
        }

        TemplateDefinition.ThemeValueSets values = d.themeValues();
        if (values == null || empty(values.headingFont()) || empty(values.bodyFont())
                || empty(values.cornerRadius()) || empty(values.density())) {
            problems.add(where + ": themeValues must list headingFont, bodyFont, cornerRadius, and density values");
            return;
        }
        if (d.defaultTheme() == null) {
            problems.add(where + ": defaultTheme is required");
        } else {
            completeStyle(where + " defaultTheme", d.defaultTheme(), problems);
            allowed(where + " defaultTheme", d.defaultTheme(), values, problems);
        }
        if (d.stylePresets() == null || d.stylePresets().isEmpty()) {
            problems.add(where + ": stylePresets must offer at least one preset");
        } else {
            for (String key : d.stylePresets()) {
                StylePresetFile preset = presets.get(key);
                if (preset == null) {
                    problems.add(where + ": stylePresets names the unknown preset " + key);
                } else {
                    allowed(where + " preset " + key, preset.values(), values, problems);
                }
            }
            if (!d.stylePresets().contains(d.defaultStylePreset())) {
                problems.add(where + ": defaultStylePreset must be one of stylePresets");
            }
        }
        shell(where, d.shellSchema(), d.defaultShell(), problems);
        if (d.supportedSurfaces() != null) {
            for (Map.Entry<Surface, Map<String, Object>> entry : d.runtimeDefaultsOrEmpty().entrySet()) {
                if (!d.supportedSurfaces().contains(entry.getKey())) {
                    problems.add(where + ": runtimeDefaults names the unsupported surface " + entry.getKey());
                }
                Set<String> keys = UpdateProjectSurfacesUseCase.configurationKeys(entry.getKey());
                for (String key : entry.getValue() == null ? Set.<String>of() : entry.getValue().keySet()) {
                    if (!keys.contains(key)) {
                        // The surfaces endpoint would refuse the key on the next save (GAPS.md G-035).
                        problems.add(where + ": runtimeDefaults." + entry.getKey() + " has the unsupported key " + key);
                    }
                }
            }
        }
    }

    private static void categories(String where, List<InitialCategoryDefinition> categories, List<String> problems) {
        Set<String> codes = new HashSet<>();
        for (InitialCategoryDefinition category : categories) {
            String code = category.code();
            if (code == null || code.length() > CategoryRules.MAX_CODE || !CategoryRules.CODE.matcher(code).matches()) {
                problems.add(where + ": initialCategories has an invalid code");
                continue;
            }
            if (!codes.add(code)) {
                problems.add(where + ": initialCategories lists " + code + " twice");
            }
            if (category.scope() == null) {
                problems.add(where + ": initial category " + code + " needs a scope");
            }
            if (category.icon() != null && !CategoryRules.ICONS.contains(category.icon())) {
                problems.add(where + ": initial category " + code + " has an unknown icon");
            }
            if (category.name() == null || category.name().get("en") == null || category.name().values().stream()
                    .anyMatch(name -> name == null || name.isBlank() || name.length() > CategoryRules.MAX_NAME)) {
                problems.add(where + ": initial category " + code + " needs a name in en and no blank name");
            }
        }
    }

    private static void shell(String where, Map<String, List<String>> schema, Map<String, String> defaults,
                              List<String> problems) {
        if (schema == null || defaults == null) {
            problems.add(where + ": shellSchema and defaultShell are required");
            return;
        }
        if (!schema.keySet().equals(Set.copyOf(ShellKeys.CHOSEN))) {
            problems.add(where + ": shellSchema must list exactly " + ShellKeys.CHOSEN);
        }
        if (!defaults.keySet().equals(schema.keySet())) {
            problems.add(where + ": defaultShell must set every shellSchema key");
        }
        schema.forEach((key, allowed) -> {
            if (allowed == null || allowed.isEmpty()) {
                problems.add(where + ": shellSchema." + key + " lists no value");
            } else if (defaults.get(key) != null && !allowed.contains(defaults.get(key))) {
                problems.add(where + ": defaultShell." + key + " is not among its shellSchema values");
            }
        });
    }

    private static void completeStyle(String where, StyleValues values, List<String> problems) {
        values.asMap().forEach((field, value) -> {
            if (value == null) {
                problems.add(where + ": " + field + " is required");
            }
        });
        for (String color : List.of(values.primaryColor(), values.secondaryColor(), values.backgroundColor(),
                values.textColor())) {
            if (color != null && !Colors.valid(color)) {
                problems.add(where + ": colors must be #RRGGBB");
            }
        }
        check(where, "headingFont", values.headingFont(), ThemeFont::parse, problems);
        check(where, "bodyFont", values.bodyFont(), ThemeFont::parse, problems);
        check(where, "cornerRadius", values.cornerRadius(), v -> parse(CornerRadius.class, v), problems);
        check(where, "density", values.density(), v -> parse(Density.class, v), problems);
        for (Colors.ContrastFailure failure : Colors.contrastFailures(values)) {
            problems.add(where + ": " + failure.field() + " on backgroundColor is " + Colors.display(failure.ratio())
                    + ":1, below " + failure.minimum() + ":1");
        }
    }

    private static void allowed(String where, StyleValues style, TemplateDefinition.ThemeValueSets values,
                                List<String> problems) {
        for (String field : List.of("headingFont", "bodyFont", "cornerRadius", "density")) {
            String value = style.asMap().get(field);
            if (value != null && !values.allows(field, value)) {
                problems.add(where + ": " + field + " is not among the Template's themeValues");
            }
        }
    }

    private static void check(String where, String field, String value, Function<String, Object> parser,
                              List<String> problems) {
        if (value != null && parser.apply(value) == null) {
            problems.add(where + ": " + field + " has an unknown value");
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(value)) {
                return constant;
            }
        }
        return null;
    }

    private static void texts(String where, Map<String, String> texts, String member, List<String> problems) {
        if (texts == null || texts.get("en") == null || texts.get("en").isBlank()) {
            problems.add(where + ": " + member + " needs an en value");
        }
    }

    private static boolean empty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
