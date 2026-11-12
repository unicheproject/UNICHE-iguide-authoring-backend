package eu.uniche.iguide.initialization.application;

import eu.uniche.iguide.project.application.ProjectInitializationReader;
import eu.uniche.iguide.project.infrastructure.ProjectRepository;
import eu.uniche.iguide.settings.domain.ApplicationStatus;
import eu.uniche.iguide.settings.infrastructure.ProjectSettingsRepository;
import eu.uniche.iguide.surface.application.SurfaceLookup;
import eu.uniche.iguide.surface.application.SurfaceView;
import eu.uniche.iguide.template.application.TemplateLookup;
import eu.uniche.iguide.theme.infrastructure.ProjectThemeRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** {@link ProjectInitializationReader} over the local rows initialization writes (DECISIONS.md D-040). */
@Component
public class InitializationSummaryReader implements ProjectInitializationReader {

    private final ProjectRepository projects;
    private final TemplateLookup templates;
    private final ProjectThemeRepository themes;
    private final ProjectSettingsRepository settings;
    private final SurfaceLookup surfaces;

    public InitializationSummaryReader(ProjectRepository projects, TemplateLookup templates,
                                       ProjectThemeRepository themes, ProjectSettingsRepository settings,
                                       SurfaceLookup surfaces) {
        this.projects = projects;
        this.templates = templates;
        this.themes = themes;
        this.settings = settings;
        this.surfaces = surfaces;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectInitialization read(UUID projectId) {
        Instant initializedAt = projects.findById(projectId).map(p -> p.getInitializedAt()).orElse(null);
        AppliedTemplate template = templates.applied(projectId)
                .map(applied -> new AppliedTemplate(applied.template().getId(), applied.template().getKey(),
                        applied.template().getVersion(), applied.template().getName()))
                .orElse(null);
        String presetKey = themes.findById(projectId).map(theme -> theme.getStylePresetKey()).orElse(null);
        String status = settings.findById(projectId).map(s -> s.getApplicationStatus())
                .orElse(ApplicationStatus.DRAFT).name();
        return new ProjectInitialization(initializedAt, template, presetKey, status,
                surfaces.all(projectId).stream().filter(SurfaceView::enabled).map(v -> v.surface().name()).toList());
    }
}
