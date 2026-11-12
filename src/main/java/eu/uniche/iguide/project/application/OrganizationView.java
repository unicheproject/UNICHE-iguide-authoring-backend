package eu.uniche.iguide.project.application;

import eu.uniche.iguide.platform.catalogue.CatalogueBadResponseException;
import eu.uniche.iguide.platform.catalogue.CatalogueOrganizationDto;
import java.util.UUID;

/** A Catalogue organization as the application layer hands it upward. Nothing of it is stored here. */
public record OrganizationView(UUID id, String name, String slug, String status) {

    public static OrganizationView of(CatalogueOrganizationDto dto) {
        UUID id;
        try {
            id = UUID.fromString(dto.id());
        } catch (IllegalArgumentException | NullPointerException unusable) {
            throw new CatalogueBadResponseException(
                    "The Catalogue organization id is missing or not a UUID", unusable);
        }
        return new OrganizationView(id, dto.name(), dto.slug(), dto.status());
    }
}
