package eu.uniche.iguide.template.domain;

import java.util.List;

/** The keys of the application shell ({@code 04} section 9); a Template's {@code shellSchema} uses these. */
public final class ShellKeys {

    /** Free text, not in a Template's {@code shellSchema}: null means the Project name. */
    public static final String BRAND_WORDMARK = "brandWordmark";

    /** The keys whose values a Template's {@code shellSchema} lists, in display order. */
    public static final List<String> CHOSEN = List.of("languageSwitcher", "search", "feedback", "mobileMenu",
            "kioskHome");

    private ShellKeys() {
    }
}
