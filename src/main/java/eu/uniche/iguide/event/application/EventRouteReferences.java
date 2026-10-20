package eu.uniche.iguide.event.application;

import eu.uniche.iguide.event.infrastructure.EventRepository;
import eu.uniche.iguide.route.application.RouteReferenceSource;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Non-archived Events linked to a Route ({@code 03} section 1: the link columns of {@code Event}). */
@Component
public class EventRouteReferences implements RouteReferenceSource {

    private final EventRepository events;
    private final EventRules rules;

    public EventRouteReferences(EventRepository events, EventRules rules) {
        this.events = events;
        this.rules = rules;
    }

    @Override
    public List<ResourceReference> referencesTo(UUID projectId, UUID id) {
        return rules.asReferences(projectId, events.findByProjectIdAndRouteIdAndStatusNotOrderBySortOrderAsc(projectId, id, ResourceStatus.ARCHIVED));
    }
}
