package eu.uniche.iguide.platform.catalogue;

/** Body the Catalogue expects when updating a Project. The Catalogue owns name and status. */
public record CatalogueUpdateProjectRequest(String name) {
}
