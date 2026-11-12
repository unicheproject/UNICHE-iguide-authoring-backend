package eu.uniche.iguide.project.api;

import eu.uniche.iguide.project.application.OrganizationView;
import java.util.UUID;

/** This API's representation of a Catalogue organization. Every field is Catalogue-owned. */
public record OrganizationResponse(UUID id, String name, String slug, String status) {

    public static OrganizationResponse from(OrganizationView view) {
        return new OrganizationResponse(view.id(), view.name(), view.slug(), view.status());
    }
}
