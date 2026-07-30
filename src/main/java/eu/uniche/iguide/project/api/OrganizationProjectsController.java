package eu.uniche.iguide.project.api;

import eu.uniche.iguide.project.application.CreateProjectUseCase;
import eu.uniche.iguide.project.application.ListDeletedProjectsPassthroughQuery;
import eu.uniche.iguide.project.application.ListOrganizationProjectsPassthroughQuery;
import eu.uniche.iguide.project.application.ProjectView;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organization-scoped Project operations.
 *
 * <p>Note the spelling: this API uses {@code organizations} while the Catalogue's own path segment is
 * {@code organisations}. The client translates, and neither side should be changed to match the other.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/projects")
public class OrganizationProjectsController {

    private final CreateProjectUseCase createProject;
    private final ListOrganizationProjectsPassthroughQuery listProjects;
    private final ListDeletedProjectsPassthroughQuery listDeletedProjects;

    public OrganizationProjectsController(
            CreateProjectUseCase createProject,
            ListOrganizationProjectsPassthroughQuery listProjects,
            ListDeletedProjectsPassthroughQuery listDeletedProjects) {
        this.createProject = createProject;
        this.listProjects = listProjects;
        this.listDeletedProjects = listDeletedProjects;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@PathVariable UUID organizationId,
                                                  @Valid @RequestBody CreateProjectRequest request) {
        ProjectView created = createProject.execute(organizationId, request.name(), request.slug());
        return ResponseEntity
                .created(URI.create("/api/v1/projects/" + created.id()))
                .body(ProjectResponse.from(created));
    }

    @GetMapping
    public List<ProjectResponse> list(@PathVariable UUID organizationId) {
        return listProjects.execute(organizationId).stream().map(ProjectResponse::from).toList();
    }

    @GetMapping("/deleted")
    public List<ProjectResponse> listDeleted(@PathVariable UUID organizationId) {
        return listDeletedProjects.execute(organizationId).stream()
                .map(ProjectResponse::from)
                .toList();
    }
}
