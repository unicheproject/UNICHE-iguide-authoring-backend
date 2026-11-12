package eu.uniche.iguide.theme.application;

import eu.uniche.iguide.template.application.StyleValues;
import eu.uniche.iguide.theme.domain.ProjectTheme;

/**
 * A Project's theme with what the CMS derives from it ({@code 04} sections 9 to 11).
 *
 * @param effective           the Template's {@code defaultTheme} overridden by the Project's typed values
 * @param stylePresetModified true when an effective value differs from the preset's; the CMS then says Custom
 */
public record ThemeView(ProjectTheme theme, StyleValues effective, boolean stylePresetModified,
                        Contrast contrast) {

    /** WCAG 2.1 ratios of the effective colors against the effective background, rounded down; null if unset. */
    public record Contrast(Double textOnBackground, Double primaryOnBackground, Double secondaryOnBackground,
                           boolean passes) {
    }
}
