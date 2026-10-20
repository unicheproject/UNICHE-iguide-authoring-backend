package eu.uniche.iguide.guide.application;

import eu.uniche.iguide.guide.domain.GuideTargetType;
import eu.uniche.iguide.place.application.PlaceReferenceSource;
import eu.uniche.iguide.resource.domain.ResourceReference;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Guides with a REFERENCE block on a Place ({@code 03} section 1: {@code GuideBlock} rows). */
@Component
public class GuidePlaceReferences implements PlaceReferenceSource {

    private final GuideRules rules;

    public GuidePlaceReferences(GuideRules rules) {
        this.rules = rules;
    }

    @Override
    public List<ResourceReference> referencesTo(UUID projectId, UUID id) {
        return rules.referencesTo(projectId, GuideTargetType.PLACE, id);
    }

    /** A Guide refers to a Place but does not map it. */
    @Override
    public boolean linksPlace(UUID projectId, UUID placeId) {
        return false;
    }
}
