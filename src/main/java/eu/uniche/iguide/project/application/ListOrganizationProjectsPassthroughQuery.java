package eu.uniche.iguide.project.application;

import eu.uniche.iguide.platform.catalogue.CatalogueClient;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Lists the Projects of an organization.
 *
 * <p>A passthrough: it touches no local state at all, neither reading companion rows nor writing them.
 * The Catalogue decides which Projects this subject may see, and the name of this class says so without
 * anyone having to open it — the other classes in this package do maintain local state.
 */
@Service
public class ListOrganizationProjectsPassthroughQuery {

    private final CatalogueClient catalogueClient;

    public ListOrganizationProjectsPassthroughQuery(CatalogueClient catalogueClient) {
        this.catalogueClient = catalogueClient;
    }

    public List<ProjectView> execute(UUID organizationId) {
        return catalogueClient.listProjectsForOrganization(organizationId).stream()
                .map(ProjectView::ofCatalogueOnly)
                .toList();
    }
}
