package eu.uniche.iguide.route.application;

import eu.uniche.iguide.resource.domain.ResourceReference;
import java.util.List;
import java.util.UUID;

/**
 * Implemented by each kind that can refer to a Route (Guides now; Events, Navigation, and Triggers as
 * they land), so this package learns of references without depending on those kinds.
 */
public interface RouteReferenceSource {

    /** Non-archived rows that refer to the Route; these block its deactivation and archival. */
    List<ResourceReference> referencesTo(UUID projectId, UUID routeId);
}
