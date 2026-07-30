package eu.uniche.iguide.project.application;

import eu.uniche.iguide.platform.catalogue.CatalogueClient;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Lists the deleted Projects of an organization, so a manager can choose one to restore. Also a
 * passthrough: the local companion rows are not consulted, because the Catalogue is authoritative about
 * what is deleted and this backend's soft-delete flag is only ever a consequence of that.
 */
@Service
public class ListDeletedProjectsPassthroughQuery {

    private final CatalogueClient catalogueClient;

    public ListDeletedProjectsPassthroughQuery(CatalogueClient catalogueClient) {
        this.catalogueClient = catalogueClient;
    }

    public List<ProjectView> execute(UUID organizationId) {
        return catalogueClient.listDeletedProjectsForOrganization(organizationId).stream()
                .map(ProjectView::ofCatalogueOnly)
                .toList();
    }
}
