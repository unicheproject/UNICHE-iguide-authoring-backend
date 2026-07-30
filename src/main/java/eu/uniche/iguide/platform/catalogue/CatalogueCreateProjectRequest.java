package eu.uniche.iguide.platform.catalogue;

/** Body the Catalogue expects when creating a Project under an organization. */
public record CatalogueCreateProjectRequest(String name, String slug, String toolSlug) {
}
