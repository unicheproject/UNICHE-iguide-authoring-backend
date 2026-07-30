package eu.uniche.iguide.project.application;

import eu.uniche.iguide.platform.catalogue.CatalogueClient;
import eu.uniche.iguide.platform.catalogue.CatalogueNotFoundException;
import eu.uniche.iguide.platform.catalogue.CatalogueProjectDto;
import eu.uniche.iguide.platform.catalogue.CatalogueUpdateProjectRequest;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.project.domain.Project;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Updates the Catalogue-owned name and the locally owned description.
 *
 * <p>The access check runs first, then the Catalogue write, then the companion synchronization. The
 * local description is written in the same companion row afterwards, because the Catalogue neither
 * stores nor cares about it.
 */
@Service
public class UpdateProjectUseCase {

    private final ProjectAccessGateway accessGateway;
    private final CatalogueClient catalogueClient;
    private final ProjectCompanionSyncService companionSync;

    public UpdateProjectUseCase(ProjectAccessGateway accessGateway,
                                CatalogueClient catalogueClient,
                                ProjectCompanionSyncService companionSync) {
        this.accessGateway = accessGateway;
        this.catalogueClient = catalogueClient;
        this.companionSync = companionSync;
    }

    public ProjectView execute(UUID projectId, UnichePrincipal principal, String name,
                               String localDescription) {
        accessGateway.requireAccess(projectId, principal);

        CatalogueProjectDto updated;
        try {
            updated = catalogueClient.updateProject(projectId,
                    new CatalogueUpdateProjectRequest(name));
        } catch (CatalogueNotFoundException gone) {
            companionSync.softDelete(projectId);
            throw gone;
        }

        companionSync.upsert(updated);
        // An absent description means "leave it alone", not "clear it": the two are different requests
        // and only an explicitly supplied empty string clears the field.
        Project companion = localDescription == null
                ? companionSync.get(projectId)
                : companionSync.setLocalDescription(projectId, localDescription);
        return ProjectView.ofCompanion(companion);
    }
}
