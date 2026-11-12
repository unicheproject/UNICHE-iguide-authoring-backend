package eu.uniche.iguide.language.application;

import eu.uniche.iguide.language.domain.TextDirection;
import java.util.Map;
import java.util.Optional;

/**
 * The visitor-facing name and direction of the languages a Template may propose ({@code 03} section 3,
 * {@code 04} section 6 {@code defaultLanguages}), so initialization can create a language from its code alone
 * (DECISIONS.md D-040). An author can still name any other language explicitly.
 */
public final class KnownLanguages {

    /** The language's own name and its direction. */
    public record Known(String displayName, TextDirection direction) {
    }

    private static final Map<String, Known> LANGUAGES = Map.ofEntries(
            Map.entry("el", new Known("Ελληνικά", TextDirection.LTR)),
            Map.entry("en", new Known("English", TextDirection.LTR)),
            Map.entry("fr", new Known("Français", TextDirection.LTR)),
            Map.entry("de", new Known("Deutsch", TextDirection.LTR)),
            Map.entry("it", new Known("Italiano", TextDirection.LTR)),
            Map.entry("es", new Known("Español", TextDirection.LTR)),
            Map.entry("pt", new Known("Português", TextDirection.LTR)),
            Map.entry("nl", new Known("Nederlands", TextDirection.LTR)),
            Map.entry("bg", new Known("Български", TextDirection.LTR)),
            Map.entry("ro", new Known("Română", TextDirection.LTR)),
            Map.entry("tr", new Known("Türkçe", TextDirection.LTR)),
            Map.entry("pl", new Known("Polski", TextDirection.LTR)),
            Map.entry("cs", new Known("Čeština", TextDirection.LTR)),
            Map.entry("hu", new Known("Magyar", TextDirection.LTR)),
            Map.entry("sr", new Known("Српски", TextDirection.LTR)),
            Map.entry("hr", new Known("Hrvatski", TextDirection.LTR)),
            Map.entry("sl", new Known("Slovenščina", TextDirection.LTR)),
            Map.entry("ru", new Known("Русский", TextDirection.LTR)),
            Map.entry("uk", new Known("Українська", TextDirection.LTR)),
            Map.entry("zh", new Known("中文", TextDirection.LTR)),
            Map.entry("ja", new Known("日本語", TextDirection.LTR)),
            Map.entry("ar", new Known("العربية", TextDirection.RTL)),
            Map.entry("he", new Known("עברית", TextDirection.RTL)),
            Map.entry("fa", new Known("فارسی", TextDirection.RTL)));

    private KnownLanguages() {
    }

    public static Optional<Known> find(String languageCode) {
        return languageCode == null ? Optional.empty() : Optional.ofNullable(LANGUAGES.get(languageCode));
    }
}
