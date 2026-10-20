package eu.uniche.iguide.resource.application;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Slugs generated from the default-language title on creation ({@code 03} section 5): accents are
 * dropped, anything else outside {@code a-z0-9} becomes a hyphen, and a number is appended until the slug
 * is free. A title in a non-Latin script yields the fallback, which the author can edit.
 */
public final class SlugGenerator {

    private SlugGenerator() {
    }

    public static String generate(String title, String fallback, Predicate<String> taken) {
        String base = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (base.isEmpty()) {
            base = fallback;
        }
        if (base.length() > ResourceRules.MAX_SLUG - 10) {
            base = base.substring(0, ResourceRules.MAX_SLUG - 10).replaceAll("-+$", "");
        }
        String candidate = base;
        for (int n = 2; taken.test(candidate); n++) {
            candidate = base + "-" + n;
        }
        return candidate;
    }
}
