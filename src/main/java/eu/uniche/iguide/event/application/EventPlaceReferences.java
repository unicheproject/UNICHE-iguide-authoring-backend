package eu.uniche.iguide.event.application;

import eu.uniche.iguide.event.infrastructure.EventRepository;
import eu.uniche.iguide.place.application.PlaceReferenceSource;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Non-archived Events linked to a Place ({@code 03} section 1: the link columns of {@code Event}). */
@Component
public class EventPlaceReferences implements PlaceReferenceSource {

    private final EventRepository events;
    private final EventRules rules;

    public EventPlaceReferences(EventRepository events, EventRules rules) {
        this.events = events;
        this.rules = rules;
    }

    @Override
    public List<ResourceReference> referencesTo(UUID projectId, UUID id) {
        return rules.asReferences(projectId, events.findByProjectIdAndPlaceIdAndStatusNotOrderBySortOrderAsc(projectId, id, ResourceStatus.ARCHIVED));
    }

    /** An Event happens at a Place but does not map it. */
    @Override
    public boolean linksPlace(UUID projectId, UUID placeId) {
        return false;
    }
}
