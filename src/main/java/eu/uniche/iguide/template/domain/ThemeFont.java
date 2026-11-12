package eu.uniche.iguide.template.domain;

/**
 * The platform font set a Template's {@code themeValues} chooses from ({@code 04} sections 6 and 9; {@code 09}
 * section 19 is open on who owns it). Each key names one web font family, which the CMS and the runtime load by
 * {@link #family()}; the CMS offers them as the named pairs of the prototype (DECISIONS.md D-040).
 */
public enum ThemeFont {
    ARCHIVO("Archivo"),
    SPECTRAL("Spectral"),
    BARLOW("Barlow"),
    BARLOW_CONDENSED("Barlow Condensed"),
    SPACE_GROTESK("Space Grotesk"),
    IBM_PLEX_SANS("IBM Plex Sans"),
    IBM_PLEX_MONO("IBM Plex Mono"),
    NEWSREADER("Newsreader"),
    LIBRE_FRANKLIN("Libre Franklin"),
    SAIRA_CONDENSED("Saira Condensed"),
    SORA("Sora"),
    SYNE("Syne"),
    BALOO_2("Baloo 2");

    private final String family;

    ThemeFont(String family) {
        this.family = family;
    }

    public String family() {
        return family;
    }

    public static ThemeFont parse(String value) {
        for (ThemeFont font : values()) {
            if (font.name().equals(value)) {
                return font;
            }
        }
        return null;
    }
}
