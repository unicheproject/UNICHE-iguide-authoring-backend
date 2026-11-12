package eu.uniche.iguide.theme.application;

import eu.uniche.iguide.readiness.application.ReadinessCheck;
import eu.uniche.iguide.readiness.application.ReadinessContext;
import eu.uniche.iguide.readiness.application.ReadinessReport;
import eu.uniche.iguide.template.application.Colors;
import org.springframework.stereotype.Component;

/**
 * {@code THEME_CONTRAST_FAILED} ({@code 07} section 14, {@code 14} section 2): the effective theme of an initialized
 * Project misses a WCAG 2.1 AA contrast ratio, at path {@code theme.<field>}. {@code PUT .../theme} refuses such a
 * theme, so this reports only a theme whose Template defaults fill the gap badly.
 */
@Component
public class ThemeReadiness implements ReadinessCheck {

    private final EffectiveThemeService themes;

    public ThemeReadiness(EffectiveThemeService themes) {
        this.themes = themes;
    }

    @Override
    public void check(ReadinessContext context, ReadinessReport report) {
        themes.resolve(context.projectId()).ifPresent(view -> Colors.contrastFailures(view.effective()).forEach(
                failure -> report.block("PROJECT", null, null, null, "theme." + failure.field(),
                        "THEME_CONTRAST_FAILED")));
    }
}
