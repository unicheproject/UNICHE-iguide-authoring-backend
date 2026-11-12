package eu.uniche.iguide.project.api;

import eu.uniche.iguide.project.application.ListOrganizationsPassthroughQuery;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The organizations the current subject may see, relayed from the Catalogue.
 *
 * <p>Note the spelling: this API uses {@code organizations} while the Catalogue's own path segment is
 * {@code organisations}. The client translates, and neither side should be changed to match the other.
 */
@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationsController {

    private final ListOrganizationsPassthroughQuery listOrganizations;

    public OrganizationsController(ListOrganizationsPassthroughQuery listOrganizations) {
        this.listOrganizations = listOrganizations;
    }

    @GetMapping
    public List<OrganizationResponse> list() {
        return listOrganizations.execute().stream().map(OrganizationResponse::from).toList();
    }
}
