package eu.uniche.iguide.surface.application;

import eu.uniche.iguide.surface.domain.ProjectSurface;
import eu.uniche.iguide.surface.domain.Surface;
import eu.uniche.iguide.surface.infrastructure.ProjectSurfaceRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Writes the surfaces chosen at Project initialization ({@code 04} section 8 step 7). Callers have passed the
 * Project access check and validated the choice against the Template; this runs inside their transaction.
 */
@Component
public class SurfaceWriter {

    private final ProjectSurfaceRepository surfaces;

    public SurfaceWriter(ProjectSurfaceRepository surfaces) {
        this.surfaces = surfaces;
    }

    /**
     * Enables exactly {@code enabled} and disables every other surface. A surface's configuration keeps what the
     * author already saved (for example the WEB address set in App settings) over the Template's
     * {@code runtimeDefaults}, which fill only the keys not set yet.
     */
    public void initialize(UUID projectId, Set<Surface> enabled, Map<Surface, Map<String, Object>> runtimeDefaults,
                           Instant now) {
        for (Surface surface : Surface.values()) {
            ProjectSurface row = surfaces.findByProjectIdAndSurface(projectId, surface)
                    .orElseGet(() -> ProjectSurface.of(projectId, surface, now));
            Map<String, Object> configuration = new HashMap<>(runtimeDefaults.getOrDefault(surface, Map.of()));
            configuration.putAll(row.getConfiguration());
            row.write(enabled.contains(surface), configuration, now);
            surfaces.save(row);
        }
    }
}
