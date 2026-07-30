package eu.uniche.iguide.platform.catalogue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The Catalogue's wire representation of a Project.
 *
 * <p>This is an external system's shape and stays confined to this package. The web layer defines its
 * own response DTOs even where the fields currently match, so this API's contract does not shift when
 * the upstream one does.
 *
 * <p>Unknown properties are ignored: the Catalogue may add fields, and this backend refusing to parse
 * a richer response would be a self-inflicted outage.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogueProjectDto(String id,
                                  String orgId,
                                  String name,
                                  String slug,
                                  String status,
                                  CatalogueToolDto tool) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogueToolDto(String slug) {
    }
}
