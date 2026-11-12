package eu.uniche.iguide.theme.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.project.application.ProjectNotInitializedException;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.settings.application.ContentRevision;
import eu.uniche.iguide.template.application.StyleValues;
import eu.uniche.iguide.template.application.TemplateDefinition;
import eu.uniche.iguide.template.application.TemplateLookup;
import eu.uniche.iguide.theme.domain.ProjectTheme;
import eu.uniche.iguide.theme.infrastructure.ProjectThemeRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code PUT .../theme} ({@code 04} sections 9 to 11, {@code 12} section 3 Appearance): replaces the typed values,
 * the preset key, the logos, and the shell, validated by {@link ThemeRules}; the Template's data is never written.
 * Choosing a preset is a {@code PUT} with the preset's values, which the CMS has from {@code GET /templates}.
 */
@Service
public class UpdateProjectThemeUseCase {

    private final ProjectAccessGateway accessGateway;
    private final ProjectThemeRepository themes;
    private final TemplateLookup templates;
    private final ThemeRules rules;
    private final EffectiveThemeService effective;
    private final TransactionTemplate transactionTemplate;
    private final ContentRevision contentRevision;

    public UpdateProjectThemeUseCase(ProjectAccessGateway accessGateway, ProjectThemeRepository themes,
                                     TemplateLookup templates, ThemeRules rules, EffectiveThemeService effective,
                                     TransactionTemplate transactionTemplate,
                                     ContentRevision contentRevision) {
        this.accessGateway = accessGateway;
        this.themes = themes;
        this.templates = templates;
        this.rules = rules;
        this.effective = effective;
        this.transactionTemplate = transactionTemplate;
        this.contentRevision = contentRevision;
    }

    public ThemeView execute(UUID projectId, UnichePrincipal principal, ThemeInput input) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            TemplateDefinition definition = templates.applied(projectId)
                    .orElseThrow(ProjectNotInitializedException::new).definition();
            ProjectTheme theme = themes.findById(projectId).orElseThrow(ProjectNotInitializedException::new);
            ResourceRules.requireCurrentVersion(theme.getVersion(), input.version(), "theme");

            List<ErrorItem> errors = new ArrayList<>();
            StyleValues values = rules.values(input.values(), definition, errors);
            String presetKey = ResourceRules.trimToNull(input.stylePresetKey());
            rules.stylePreset(presetKey, definition, errors);
            Map<String, Object> shell = rules.shell(input.shell(), definition, errors);
            rules.image(projectId, input.logoAssetId(), "logoAssetId", errors);
            rules.image(projectId, input.compactLogoAssetId(), "compactLogoAssetId", errors);
            rules.image(projectId, input.faviconAssetId(), "faviconAssetId", errors);
            rules.image(projectId, input.splashAssetId(), "splashAssetId", errors);
            if (errors.isEmpty()) {
                rules.contrast(values.over(definition.defaultTheme()), errors);
            }
            ResourceRules.throwIfAny(errors, "The theme is invalid.");

            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            theme.style(values, presetKey, now);
            theme.brand(input.logoAssetId(), input.compactLogoAssetId(), input.faviconAssetId(),
                    input.splashAssetId(), now);
            theme.shell(shell, now);
            contentRevision.increment(projectId);
            return effective.view(themes.saveAndFlush(theme), definition);
        });
    }
}
