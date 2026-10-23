package eu.uniche.iguide.guide.application;

import eu.uniche.iguide.resource.domain.ResourceReference;
import java.util.List;
import java.util.UUID;

/**
 * Implemented by each kind that can refer to a Guide (Triggers now; Navigation as it lands), so this package learns
 * of references without depending on those kinds ({@code 03} section 1).
 */
public interface GuideReferenceSource {

    /** Non-archived rows that refer to the Guide; these block its deactivation and archival. */
    List<ResourceReference> referencesTo(UUID projectId, UUID guideId);
}
