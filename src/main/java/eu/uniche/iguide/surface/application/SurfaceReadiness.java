package eu.uniche.iguide.surface.application;

import eu.uniche.iguide.readiness.application.ReadinessCheck;
import eu.uniche.iguide.readiness.application.ReadinessContext;
import eu.uniche.iguide.readiness.application.ReadinessReport;
import eu.uniche.iguide.surface.domain.ProjectSurface;
import eu.uniche.iguide.surface.domain.Surface;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Readiness of the Project's surfaces ({@code 03} section 2, {@code 07} section 14; DECISIONS.md D-033): no enabled
 * surface blocks; an enabled WEB surface without its public web address warns, since QR codes and packages need it.
 */
@Component
public class SurfaceReadiness implements ReadinessCheck {

    private final SurfaceLookup surfaces;

    public SurfaceReadiness(SurfaceLookup surfaces) {
        this.surfaces = surfaces;
    }

    @Override
    public void check(ReadinessContext context, ReadinessReport report) {
        List<SurfaceView> all = surfaces.all(context.projectId());
        if (all.stream().noneMatch(SurfaceView::enabled)) {
            report.block("PROJECT", null, null, null, "surfaces", "NO_SURFACE_ENABLED");
        }
        boolean webWithoutAddress = all.stream().anyMatch(s -> s.surface() == Surface.WEB && s.enabled()
                && !(s.configuration().get(ProjectSurface.PUBLIC_BASE_URL) instanceof String url && !url.isBlank()));
        if (webWithoutAddress) {
            report.warn("PROJECT", null, null, null, "surfaces", "PUBLIC_BASE_URL_MISSING");
        }
    }
}
