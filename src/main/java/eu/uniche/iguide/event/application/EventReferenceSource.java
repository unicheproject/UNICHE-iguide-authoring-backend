package eu.uniche.iguide.event.application;

import eu.uniche.iguide.resource.domain.ResourceReference;
import java.util.List;
import java.util.UUID;

/**
 * Implemented by each kind that can refer to an Event (Guides and Interaction Triggers now; Navigation and Pages as
 * they land), so this package learns of references without depending on those kinds.
 */
public interface EventReferenceSource {

    /** Non-archived rows that refer to the Event; these block its deactivation and archival. */
    List<ResourceReference> referencesTo(UUID projectId, UUID eventId);
}
