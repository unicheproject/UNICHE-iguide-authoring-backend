package eu.uniche.iguide.surface.api;

import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.surface.application.GetProjectSurfacesQuery;
import eu.uniche.iguide.surface.application.SurfaceInput;
import eu.uniche.iguide.surface.application.SurfaceView;
import eu.uniche.iguide.surface.application.UpdateProjectSurfacesUseCase;
import eu.uniche.iguide.surface.domain.Surface;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The visitor surfaces of a Project ({@code 07} section 14, {@code 12} section 7; DECISIONS.md D-029). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/surfaces")
public class ProjectSurfacesController {

    /** One surface in requests and responses alike. */
    public record SurfaceBody(Surface surface, boolean enabled, Integer sortOrder, Map<String, Object> configuration) {

        static SurfaceBody from(SurfaceView view) {
            return new SurfaceBody(view.surface(), view.enabled(), view.sortOrder(), view.configuration());
        }
    }

    public record SurfacesBody(@NotNull List<SurfaceBody> surfaces) {

        static SurfacesBody from(List<SurfaceView> views) {
            return new SurfacesBody(views.stream().map(SurfaceBody::from).toList());
        }
    }

    private final GetProjectSurfacesQuery getSurfaces;
    private final UpdateProjectSurfacesUseCase updateSurfaces;

    public ProjectSurfacesController(GetProjectSurfacesQuery getSurfaces, UpdateProjectSurfacesUseCase updateSurfaces) {
        this.getSurfaces = getSurfaces;
        this.updateSurfaces = updateSurfaces;
    }

    @GetMapping
    public SurfacesBody get(@PathVariable UUID projectId) {
        return SurfacesBody.from(getSurfaces.execute(projectId, CurrentPrincipal.require()));
    }

    @PutMapping
    public SurfacesBody update(@PathVariable UUID projectId, @Valid @RequestBody SurfacesBody request) {
        return SurfacesBody.from(updateSurfaces.execute(projectId, CurrentPrincipal.require(), request.surfaces().stream()
                .map(s -> new SurfaceInput(s.surface(), s.enabled(), s.configuration()))
                .toList()));
    }
}
