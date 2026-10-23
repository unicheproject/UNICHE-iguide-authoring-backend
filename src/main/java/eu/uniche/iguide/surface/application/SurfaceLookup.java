package eu.uniche.iguide.surface.application;

import eu.uniche.iguide.surface.domain.ProjectSurface;
import eu.uniche.iguide.surface.domain.Surface;
import eu.uniche.iguide.surface.infrastructure.ProjectSurfaceRepository;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The surfaces of a Project for other packages; callers have passed the Project access check. */
@Component
public class SurfaceLookup {

    private final ProjectSurfaceRepository surfaces;

    public SurfaceLookup(ProjectSurfaceRepository surfaces) {
        this.surfaces = surfaces;
    }

    /** Every surface in order, a missing row read as disabled with no configuration. */
    public List<SurfaceView> all(UUID projectId) {
        Map<Surface, ProjectSurface> stored = new EnumMap<>(Surface.class);
        surfaces.findByProjectIdOrderBySortOrderAsc(projectId).forEach(row -> stored.put(row.getSurface(), row));
        List<SurfaceView> result = new ArrayList<>();
        for (Surface surface : Surface.values()) {
            ProjectSurface row = stored.get(surface);
            result.add(row == null
                    ? new SurfaceView(surface, false, surface.ordinal(), Map.of())
                    : new SurfaceView(surface, row.isEnabled(), row.getSortOrder(), row.getConfiguration()));
        }
        return result;
    }

    /**
     * The origin the web build is hosted at ({@code 03} section 2), while the WEB surface is enabled; QR codes and
     * absolute links need it ({@code 11} section 9).
     */
    public Optional<String> publicBaseUrl(UUID projectId) {
        return surfaces.findByProjectIdAndSurface(projectId, Surface.WEB)
                .filter(ProjectSurface::isEnabled)
                .map(ProjectSurface::publicBaseUrl);
    }
}
