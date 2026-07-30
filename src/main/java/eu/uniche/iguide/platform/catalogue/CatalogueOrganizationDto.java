package eu.uniche.iguide.platform.catalogue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The Catalogue's wire representation of an organization. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogueOrganizationDto(String id, String name, String slug, String status) {
}
