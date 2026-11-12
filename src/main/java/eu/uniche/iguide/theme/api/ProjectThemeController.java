package eu.uniche.iguide.theme.api;

import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.template.application.StyleValues;
import eu.uniche.iguide.theme.application.GetProjectThemeQuery;
import eu.uniche.iguide.theme.application.ThemeInput;
import eu.uniche.iguide.theme.application.ThemeView;
import eu.uniche.iguide.theme.application.UpdateProjectThemeUseCase;
import eu.uniche.iguide.theme.domain.ProjectTheme;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The theme of a Project ({@code 04} sections 9 to 11, {@code 07} section 14; DECISIONS.md D-040). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/theme")
public class ProjectThemeController {

    /**
     * The stored theme, then what the CMS derives: {@code effective} values over the Template defaults,
     * {@code stylePresetModified} (Custom), and the contrast ratios of the effective colors.
     */
    public record ThemeResponse(String stylePresetKey, boolean stylePresetModified, String headingFont,
                                String bodyFont, String primaryColor, String secondaryColor, String backgroundColor,
                                String textColor, String cornerRadius, String density, UUID logoAssetId,
                                UUID compactLogoAssetId, UUID faviconAssetId, UUID splashAssetId,
                                Map<String, Object> shell, StyleValues effective, ThemeView.Contrast contrast,
                                long version, Instant updatedAt) {

        static ThemeResponse from(ThemeView view) {
            ProjectTheme t = view.theme();
            return new ThemeResponse(t.getStylePresetKey(), view.stylePresetModified(), t.getHeadingFont(),
                    t.getBodyFont(), t.getPrimaryColor(), t.getSecondaryColor(), t.getBackgroundColor(),
                    t.getTextColor(), t.getCornerRadius(), t.getDensity(), t.getLogoAssetId(),
                    t.getCompactLogoAssetId(), t.getFaviconAssetId(), t.getSplashAssetId(), t.getShell(),
                    view.effective(), view.contrast(), t.getVersion(), t.getUpdatedAt());
        }
    }

    /** The complete theme; a typed value left {@code null} takes the Template default, a shell key left out too. */
    public record UpdateThemeRequest(String stylePresetKey, String headingFont, String bodyFont, String primaryColor,
                                     String secondaryColor, String backgroundColor, String textColor,
                                     String cornerRadius, String density, UUID logoAssetId, UUID compactLogoAssetId,
                                     UUID faviconAssetId, UUID splashAssetId, Map<String, Object> shell,
                                     Long version) {
    }

    private final GetProjectThemeQuery getTheme;
    private final UpdateProjectThemeUseCase updateTheme;

    public ProjectThemeController(GetProjectThemeQuery getTheme, UpdateProjectThemeUseCase updateTheme) {
        this.getTheme = getTheme;
        this.updateTheme = updateTheme;
    }

    @GetMapping
    public ThemeResponse get(@PathVariable UUID projectId) {
        return ThemeResponse.from(getTheme.execute(projectId, CurrentPrincipal.require()));
    }

    @PutMapping
    public ThemeResponse update(@PathVariable UUID projectId, @RequestBody UpdateThemeRequest r) {
        return ThemeResponse.from(updateTheme.execute(projectId, CurrentPrincipal.require(), new ThemeInput(
                new StyleValues(r.headingFont(), r.bodyFont(), r.primaryColor(), r.secondaryColor(),
                        r.backgroundColor(), r.textColor(), r.cornerRadius(), r.density()),
                r.stylePresetKey(), r.logoAssetId(), r.compactLogoAssetId(), r.faviconAssetId(), r.splashAssetId(),
                r.shell(), r.version())));
    }
}
