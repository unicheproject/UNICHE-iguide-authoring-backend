package eu.uniche.iguide.template.application;

import eu.uniche.iguide.readiness.application.ReadinessCheck;
import eu.uniche.iguide.readiness.application.ReadinessContext;
import eu.uniche.iguide.readiness.application.ReadinessReport;
import eu.uniche.iguide.surface.application.SurfaceLookup;
import eu.uniche.iguide.surface.application.SurfaceView;
import org.springframework.stereotype.Component;

/**
 * {@code TEMPLATE_VALIDATION_FAILED} ({@code 07} section 14, {@code 04} section 11): an enabled surface of an
 * initialized Project is not among its Template's {@code supportedSurfaces}, at path {@code surfaces}. The surfaces
 * endpoint refuses that since initialization, so only a surface enabled some other way is reported.
 */
@Component
public class TemplateReadiness implements ReadinessCheck {

    private final TemplateLookup templates;
    private final SurfaceLookup surfaces;

    public TemplateReadiness(TemplateLookup templates, SurfaceLookup surfaces) {
        this.templates = templates;
        this.surfaces = surfaces;
    }

    @Override
    public void check(ReadinessContext context, ReadinessReport report) {
        templates.applied(context.projectId()).ifPresent(applied -> {
            boolean unsupported = surfaces.all(context.projectId()).stream()
                    .filter(SurfaceView::enabled)
                    .anyMatch(view -> !applied.definition().supportedSurfaces().contains(view.surface()));
            if (unsupported) {
                report.block("PROJECT", null, null, null, "surfaces", "TEMPLATE_VALIDATION_FAILED");
            }
        });
    }
}
