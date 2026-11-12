package eu.uniche.iguide.project.application;

import eu.uniche.iguide.platform.catalogue.CatalogueClient;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Lists the organizations the current subject may see, so the Project picker can name them without
 * calling the Catalogue itself ({@code 02} §11, {@code 07} §14).
 *
 * <p>A passthrough: it touches no local state at all. The Catalogue decides which organizations this
 * subject sees (the ones it manages or belongs to, or all of them for a platform administrator), and no
 * organization data is stored here (specs {@code CLAUDE.md} inv. 4).
 */
@Service
public class ListOrganizationsPassthroughQuery {

    private final CatalogueClient catalogueClient;

    public ListOrganizationsPassthroughQuery(CatalogueClient catalogueClient) {
        this.catalogueClient = catalogueClient;
    }

    public List<OrganizationView> execute() {
        return catalogueClient.listOrganizations().stream()
                .map(OrganizationView::of)
                .toList();
    }
}
