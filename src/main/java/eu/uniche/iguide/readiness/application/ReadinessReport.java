package eu.uniche.iguide.readiness.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The blocking findings and warnings the checks add ({@code 07} section 14), with the text rules every kind shares:
 * a required text missing in the default language blocks, a text filled in the default language and empty in another
 * enabled language warns ({@code 03} section 1).
 */
public class ReadinessReport {

    private static final List<String> TYPE_ORDER = List.of("PROJECT", "PLACE", "POI", "ROUTE", "GUIDE", "EVENT",
            "CATEGORY", "MAP", "FLOOR_PLAN", "ASSET", "NAVIGATION");

    private final List<ReadinessIssue> blocking = new ArrayList<>();
    private final List<ReadinessIssue> warnings = new ArrayList<>();

    public void block(String type, UUID id, String title, String language, String path, String code) {
        blocking.add(new ReadinessIssue(type, id, title, language, path, code));
    }

    public void warn(String type, UUID id, String title, String language, String path, String code) {
        warnings.add(new ReadinessIssue(type, id, title, language, path, code));
    }

    /**
     * The text rules for one Resource. {@code texts} maps a language code to the Resource's fields in that language;
     * {@code fields} are the texts the CMS counts; {@code required} must be filled in the default language;
     * {@code untranslated} lists fields another warning already covers in every language.
     */
    public void texts(ReadinessContext context, String type, UUID id, String title,
                      Map<String, Map<String, String>> texts, List<String> fields, String required,
                      Collection<String> untranslated) {
        if (context.defaultLanguage() == null) {
            return;
        }
        Map<String, String> source = texts.getOrDefault(context.defaultLanguage(), Map.of());
        if (required != null && blank(source.get(required))) {
            block(type, id, title, context.defaultLanguage(),
                    "translations." + context.defaultLanguage() + "." + required, "MISSING_DEFAULT_LANGUAGE_VALUE");
        }
        Set<String> skip = Set.copyOf(untranslated);
        for (String language : context.otherLanguages()) {
            Map<String, String> target = texts.getOrDefault(language, Map.of());
            for (String field : fields) {
                if (!skip.contains(field) && !blank(source.get(field)) && blank(target.get(field))) {
                    warn(type, id, title, language, "translations." + language + "." + field, "MISSING_TRANSLATION");
                }
            }
        }
    }

    public static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public List<ReadinessIssue> blocking() {
        return sorted(blocking);
    }

    public List<ReadinessIssue> warnings() {
        return sorted(warnings);
    }

    /** Project-level first, then by kind, title, and path, so the answer does not depend on check order. */
    private static List<ReadinessIssue> sorted(List<ReadinessIssue> issues) {
        return issues.stream().sorted(Comparator
                        .comparingInt((ReadinessIssue i) -> TYPE_ORDER.indexOf(i.resourceType()))
                        .thenComparing(i -> i.title() == null ? "" : i.title(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(i -> String.valueOf(i.resourceId()))
                        .thenComparing(ReadinessIssue::path))
                .toList();
    }
}
