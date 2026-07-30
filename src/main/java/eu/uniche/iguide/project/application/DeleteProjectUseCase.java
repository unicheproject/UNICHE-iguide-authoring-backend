package eu.uniche.iguide.project.application;

import eu.uniche.iguide.platform.catalogue.CatalogueClient;
import eu.uniche.iguide.platform.catalogue.CatalogueNotFoundException;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Deletes a Project through the Catalogue and soft-deletes the companion row.
 *
 * <p>Authored content is left in place. The Catalogue's deletion is reversible through restore, so
 * discarding local content here would make a restore return an empty Project. Whether and when the
 * content of a permanently deleted Project is removed is part of the deletion lifecycle, which is not
 * specified yet.
 */
@Service
public class DeleteProjectUseCase {

    private final ProjectAccessGateway accessGateway;
    private final CatalogueClient catalogueClient;
    private final ProjectCompanionSyncService companionSync;

    public DeleteProjectUseCase(ProjectAccessGateway accessGateway,
                                CatalogueClient catalogueClient,
                                ProjectCompanionSyncService companionSync) {
        this.accessGateway = accessGateway;
        this.catalogueClient = catalogueClient;
        this.companionSync = companionSync;
    }

    public void execute(UUID projectId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        try {
            catalogueClient.deleteProject(projectId);
        } catch (CatalogueNotFoundException alreadyGone) {
            // Already absent upstream. Reconcile locally and report it, so the caller learns the
            // Project is gone rather than believing this call deleted it.
            companionSync.softDelete(projectId);
            throw alreadyGone;
        }
        companionSync.softDelete(projectId);
    }
}
