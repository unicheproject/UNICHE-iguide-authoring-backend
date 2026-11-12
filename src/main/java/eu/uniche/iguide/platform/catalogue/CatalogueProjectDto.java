package eu.uniche.iguide.platform.catalogue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * The Catalogue's wire representation of a Project.
 *
 * <p>This is an external system's shape and stays confined to this package. The web layer defines its
 * own response DTOs even where the fields currently match, so this API's contract does not shift when
 * the upstream one does.
 *
 * <p>Unknown properties are ignored: the Catalogue may add fields, and this backend refusing to parse
 * a richer response would be a self-inflicted outage.
 *
 * <p>{@code createdAt} and {@code updatedAt} are kept as the strings the Catalogue sent and read through
 * {@link #createdAtInstant()} and {@link #updatedAtInstant()}, for the same reason: a missing or
 * unparseable timestamp is a display gap, not a reason to refuse the Project (DECISIONS.md D-039).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogueProjectDto(String id,
                                  String orgId,
                                  String name,
                                  String slug,
                                  String status,
                                  CatalogueToolDto tool,
                                  String createdAt,
                                  String updatedAt) {

    /** The Catalogue's creation time, or {@code null} when it is absent or unparseable. */
    public Instant createdAtInstant() {
        return CatalogueTimestamps.parse(createdAt);
    }

    /** The Catalogue's last update time, or {@code null} when it is absent or unparseable. */
    public Instant updatedAtInstant() {
        return CatalogueTimestamps.parse(updatedAt);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CatalogueToolDto(String slug) {
    }
}
