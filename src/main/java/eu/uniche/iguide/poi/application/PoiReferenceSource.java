package eu.uniche.iguide.poi.application;

import eu.uniche.iguide.resource.domain.ResourceReference;
import java.util.List;
import java.util.UUID;

/**
 * Implemented by each kind that can refer to a Point of Interest (Geographic Maps and Floor Plans now;
 * Routes, Guides, Events, and Interaction Triggers as they land), so this package learns of references
 * without depending on those kinds.
 */
public interface PoiReferenceSource {

    /** Non-archived rows that refer to the POI; these block its deactivation and archival. */
    List<ResourceReference> referencesTo(UUID projectId, UUID poiId);
}
