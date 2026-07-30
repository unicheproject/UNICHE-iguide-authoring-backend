package eu.uniche.iguide.project.application;

import eu.uniche.iguide.platform.catalogue.CatalogueClient;
import eu.uniche.iguide.platform.catalogue.CatalogueProjectDto;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Restores a Project the Catalogue had deleted.
 *
 * <p>There is no local access check first, and there cannot be: the ordinary access check reads the
 * Project, and a deleted Project answers {@code 404}. The Catalogue authorizes the restore itself, and a
 * subject who may not perform it receives its {@code 403} or {@code 404} unchanged.
 *
 * <p>The companion upsert clears {@code locallyDeletedAt}, so a Project the Catalogue is answering for
 * again stops looking deleted here.
 */
@Service
public class RestoreProjectUseCase {

    private final CatalogueClient catalogueClient;
    private final ProjectCompanionSyncService companionSync;

    public RestoreProjectUseCase(CatalogueClient catalogueClient,
                                 ProjectCompanionSyncService companionSync) {
        this.catalogueClient = catalogueClient;
        this.companionSync = companionSync;
    }

    public ProjectView execute(UUID projectId) {
        CatalogueProjectDto restored = catalogueClient.restoreProject(projectId);
        return ProjectView.ofCompanion(companionSync.upsert(restored));
    }
}
