package eu.uniche.iguide.project.application;

import eu.uniche.iguide.platform.projectaccess.AccessibleProject;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.project.infrastructure.ProjectRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Reads one Project.
 *
 * <p>This is also the reconciliation path: the access check inside it repairs or soft-deletes the
 * companion row as a side effect, which is why no periodic sweep is needed.
 */
@Service
public class GetProjectQuery {

    private final ProjectAccessGateway accessGateway;
    private final ProjectRepository projectRepository;

    public GetProjectQuery(ProjectAccessGateway accessGateway, ProjectRepository projectRepository) {
        this.accessGateway = accessGateway;
        this.projectRepository = projectRepository;
    }

    // Deliberately not transactional. The access check inside writes the companion row through its own
    // transaction, and a read-only transaction opened here would forbid exactly that write.
    public ProjectView execute(UUID projectId, UnichePrincipal principal) {
        AccessibleProject project = accessGateway.requireAccessibleProject(projectId, principal);
        // The companion row exists by now: the access check upserted it. Locally owned fields are read
        // from it, never from the Catalogue.
        return projectRepository.findById(project.id())
                .map(companion -> ProjectView.of(project, companion.getLocalDescription(),
                        companion.getInitializedAt() != null))
                .orElseGet(() -> ProjectView.of(project, null, false));
    }
}
