package eu.uniche.iguide.trigger.application;

import eu.uniche.iguide.poi.application.PoiReferenceSource;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.trigger.domain.TriggerTargetType;
import eu.uniche.iguide.trigger.infrastructure.InteractionTriggerRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Non-archived triggers keep their Point of Interest in use ({@code 03} section 1). */
@Component
public class TriggerPoiReferences implements PoiReferenceSource {

    private final InteractionTriggerRepository triggers;

    public TriggerPoiReferences(InteractionTriggerRepository triggers) {
        this.triggers = triggers;
    }

    @Override
    public List<ResourceReference> referencesTo(UUID projectId, UUID poiId) {
        return triggers.findByProjectIdAndTargetResourceTypeAndTargetResourceIdAndStatusNotOrderBySortOrderAsc(projectId,
                        TriggerTargetType.POI, poiId, ResourceStatus.ARCHIVED).stream()
                .map(t -> new ResourceReference("TRIGGER", t.getId(), TriggerLabels.of(t), t.getStatus()))
                .toList();
    }
}
