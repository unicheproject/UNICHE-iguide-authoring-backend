package eu.uniche.iguide.project.api;

import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.project.application.DeleteProjectUseCase;
import eu.uniche.iguide.project.application.GetProjectQuery;
import eu.uniche.iguide.project.application.RestoreProjectUseCase;
import eu.uniche.iguide.project.application.UpdateProjectUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project-scoped lifecycle operations. All paths sit behind the per-tool context path {@code /iguide}.
 *
 * <p>The controller resolves the principal and delegates. It never calls a repository or the Catalogue
 * client itself: an access check is an application concern, and one skipped here would be invisible.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final GetProjectQuery getProject;
    private final UpdateProjectUseCase updateProject;
    private final DeleteProjectUseCase deleteProject;
    private final RestoreProjectUseCase restoreProject;

    public ProjectController(GetProjectQuery getProject,
                             UpdateProjectUseCase updateProject,
                             DeleteProjectUseCase deleteProject,
                             RestoreProjectUseCase restoreProject) {
        this.getProject = getProject;
        this.updateProject = updateProject;
        this.deleteProject = deleteProject;
        this.restoreProject = restoreProject;
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@PathVariable UUID projectId) {
        return ProjectResponse.from(getProject.execute(projectId, CurrentPrincipal.require()));
    }

    /** Exposed as {@code PATCH} although the Catalogue's own equivalent is {@code PUT}. */
    @PatchMapping("/{projectId}")
    public ProjectResponse update(@PathVariable UUID projectId,
                                  @Valid @RequestBody UpdateProjectRequest request) {
        return ProjectResponse.from(updateProject.execute(projectId, CurrentPrincipal.require(),
                request.name(), request.localDescription()));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId) {
        deleteProject.execute(projectId, CurrentPrincipal.require());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/restore")
    public ProjectResponse restore(@PathVariable UUID projectId) {
        return ProjectResponse.from(restoreProject.execute(projectId));
    }
}
