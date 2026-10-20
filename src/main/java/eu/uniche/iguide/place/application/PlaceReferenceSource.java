package eu.uniche.iguide.place.application;

import eu.uniche.iguide.resource.domain.ResourceReference;
import java.util.List;
import java.util.UUID;

/**
 * Implemented by each kind that can refer to a Place (Geographic Maps, Floor Plans, and later Events
 * and Guides), so this package learns of references without depending on those kinds.
 */
public interface PlaceReferenceSource {

    /** Non-archived rows that link the Place or place it; these block deactivation and archival. */
    List<ResourceReference> referencesTo(UUID projectId, UUID placeId);

    /** Whether a map or floor plan is linked to the Place, which fixes its environment. */
    boolean linksPlace(UUID projectId, UUID placeId);
}
