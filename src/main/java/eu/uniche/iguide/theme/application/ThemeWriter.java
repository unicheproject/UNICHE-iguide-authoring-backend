package eu.uniche.iguide.theme.application;

import eu.uniche.iguide.template.application.StyleValues;
import eu.uniche.iguide.template.domain.ShellKeys;
import eu.uniche.iguide.theme.domain.ProjectTheme;
import eu.uniche.iguide.theme.infrastructure.ProjectThemeRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Creates the theme at initialization ({@code 04} section 8 step 5): the preset's values copied into the typed
 * columns, {@code shell} from the Template's {@code defaultShell}. Runs inside the caller's transaction.
 */
@Component
public class ThemeWriter {

    private final ProjectThemeRepository themes;

    public ThemeWriter(ProjectThemeRepository themes) {
        this.themes = themes;
    }

    public void initialize(UUID projectId, String presetKey, StyleValues presetValues, Map<String, String> defaultShell,
                           Instant now) {
        ProjectTheme theme = themes.findById(projectId).orElseGet(() -> ProjectTheme.of(projectId, now));
        theme.style(presetValues, presetKey, now);
        Map<String, Object> shell = new LinkedHashMap<>();
        shell.put(ShellKeys.BRAND_WORDMARK, null);
        ShellKeys.CHOSEN.forEach(key -> shell.put(key, defaultShell.get(key)));
        theme.shell(shell, now);
        themes.save(theme);
    }
}
