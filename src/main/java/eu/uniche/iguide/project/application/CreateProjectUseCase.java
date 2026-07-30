package eu.uniche.iguide.project.application;

import eu.uniche.iguide.platform.catalogue.CatalogueClient;
import eu.uniche.iguide.platform.catalogue.CatalogueCreateProjectRequest;
import eu.uniche.iguide.platform.catalogue.CatalogueProjectDto;
import eu.uniche.iguide.platform.catalogue.ToolProperties;
import eu.uniche.iguide.platform.organization.OrganizationSnapshotService;
import eu.uniche.iguide.project.domain.Project;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Creates a Project.
 *
 * <p>The Catalogue owns projects, so it writes first and issues the canonical UUID; only then is the
 * companion row created. The two writes are not one transaction and cannot be. If the local write
 * fails, the Project exists upstream and the next read of it creates the companion row.
 *
 * <p>Initializing the new Project from a template belongs to the templates milestone and is not done
 * here yet.
 */
@Service
public class CreateProjectUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateProjectUseCase.class);

    private final CatalogueClient catalogueClient;
    private final ProjectCompanionSyncService companionSync;
    private final OrganizationSnapshotService organizationSnapshots;
    private final ToolProperties toolProperties;

    public CreateProjectUseCase(CatalogueClient catalogueClient,
                                ProjectCompanionSyncService companionSync,
                                OrganizationSnapshotService organizationSnapshots,
                                ToolProperties toolProperties) {
        this.catalogueClient = catalogueClient;
        this.companionSync = companionSync;
        this.organizationSnapshots = organizationSnapshots;
        this.toolProperties = toolProperties;
    }

    public ProjectView execute(UUID organizationId, String name, String slug) {
        CatalogueProjectDto created = catalogueClient.createProject(organizationId,
                new CatalogueCreateProjectRequest(name, slug, toolProperties.slug()));

        Project companion = companionSync.upsert(created);
        refreshOrganizationSnapshot(organizationId);

        return ProjectView.ofCompanion(companion);
    }

    /**
     * A display convenience, so a failure here must not fail a creation the Catalogue has already
     * accepted.
     */
    private void refreshOrganizationSnapshot(UUID organizationId) {
        try {
            var organization = catalogueClient.getOrganization(organizationId);
            organizationSnapshots.upsert(organizationId, organization.name(), organization.slug(),
                    organization.status());
        } catch (RuntimeException failure) {
            log.warn("Could not refresh the snapshot of organization {}", organizationId, failure);
        }
    }
}
