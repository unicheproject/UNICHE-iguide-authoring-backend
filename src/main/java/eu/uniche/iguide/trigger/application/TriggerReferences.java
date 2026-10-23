package eu.uniche.iguide.trigger.application;

import eu.uniche.iguide.event.application.EventReferenceSource;
import eu.uniche.iguide.guide.application.GuideReferenceSource;
import eu.uniche.iguide.place.application.PlaceReferenceSource;
import eu.uniche.iguide.poi.application.PoiReferenceSource;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.route.application.RouteReferenceSource;
import eu.uniche.iguide.trigger.domain.TriggerTargetType;
import eu.uniche.iguide.trigger.infrastructure.InteractionTriggerRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Non-archived triggers keep the Resource they open in use ({@code 03} section 1): one reference source per target
 * kind (DECISIONS.md D-028), all reading the same rows.
 */
@Component
public class TriggerReferences {

    private final InteractionTriggerRepository triggers;

    public TriggerReferences(InteractionTriggerRepository triggers) {
        this.triggers = triggers;
    }

    List<ResourceReference> to(UUID projectId, TriggerTargetType type, UUID targetId) {
        return triggers.findByProjectIdAndTargetResourceTypeAndTargetResourceIdAndStatusNotOrderBySortOrderAsc(projectId,
                        type, targetId, ResourceStatus.ARCHIVED).stream()
                .map(t -> new ResourceReference("TRIGGER", t.getId(), TriggerLabels.of(t), t.getStatus()))
                .toList();
    }

    @Component
    static class ToPoi implements PoiReferenceSource {
        private final TriggerReferences references;

        ToPoi(TriggerReferences references) {
            this.references = references;
        }

        @Override
        public List<ResourceReference> referencesTo(UUID projectId, UUID poiId) {
            return references.to(projectId, TriggerTargetType.POI, poiId);
        }
    }

    @Component
    static class ToPlace implements PlaceReferenceSource {
        private final TriggerReferences references;

        ToPlace(TriggerReferences references) {
            this.references = references;
        }

        @Override
        public List<ResourceReference> referencesTo(UUID projectId, UUID placeId) {
            return references.to(projectId, TriggerTargetType.PLACE, placeId);
        }

        /** A trigger does not link a map or floor plan, so it never fixes the Place's environment. */
        @Override
        public boolean linksPlace(UUID projectId, UUID placeId) {
            return false;
        }
    }

    @Component
    static class ToRoute implements RouteReferenceSource {
        private final TriggerReferences references;

        ToRoute(TriggerReferences references) {
            this.references = references;
        }

        @Override
        public List<ResourceReference> referencesTo(UUID projectId, UUID routeId) {
            return references.to(projectId, TriggerTargetType.ROUTE, routeId);
        }
    }

    @Component
    static class ToGuide implements GuideReferenceSource {
        private final TriggerReferences references;

        ToGuide(TriggerReferences references) {
            this.references = references;
        }

        @Override
        public List<ResourceReference> referencesTo(UUID projectId, UUID guideId) {
            return references.to(projectId, TriggerTargetType.GUIDE, guideId);
        }
    }

    @Component
    static class ToEvent implements EventReferenceSource {
        private final TriggerReferences references;

        ToEvent(TriggerReferences references) {
            this.references = references;
        }

        @Override
        public List<ResourceReference> referencesTo(UUID projectId, UUID eventId) {
            return references.to(projectId, TriggerTargetType.EVENT, eventId);
        }
    }
}
