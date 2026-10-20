package eu.uniche.iguide.guide.application;

import eu.uniche.iguide.guide.domain.GuideTargetType;
import eu.uniche.iguide.poi.application.PoiReferenceSource;
import eu.uniche.iguide.resource.domain.ResourceReference;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Guides with a REFERENCE block on a Point of Interest ({@code 03} section 1: {@code GuideBlock} rows). */
@Component
public class GuidePoiReferences implements PoiReferenceSource {

    private final GuideRules rules;

    public GuidePoiReferences(GuideRules rules) {
        this.rules = rules;
    }

    @Override
    public List<ResourceReference> referencesTo(UUID projectId, UUID id) {
        return rules.referencesTo(projectId, GuideTargetType.POI, id);
    }
}
