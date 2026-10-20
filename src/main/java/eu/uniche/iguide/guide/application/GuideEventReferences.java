package eu.uniche.iguide.guide.application;

import eu.uniche.iguide.event.application.EventReferenceSource;
import eu.uniche.iguide.guide.domain.GuideTargetType;
import eu.uniche.iguide.resource.domain.ResourceReference;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Guides with a REFERENCE block on an Event ({@code 03} section 1: {@code GuideBlock} rows). */
@Component
public class GuideEventReferences implements EventReferenceSource {

    private final GuideRules rules;

    public GuideEventReferences(GuideRules rules) {
        this.rules = rules;
    }

    @Override
    public List<ResourceReference> referencesTo(UUID projectId, UUID id) {
        return rules.referencesTo(projectId, GuideTargetType.EVENT, id);
    }
}
