package eu.uniche.iguide.event.api;

import eu.uniche.iguide.asset.application.ResourceAssetUseCase;
import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.event.application.EventOccurrenceUseCase;
import eu.uniche.iguide.event.application.EventRules;
import eu.uniche.iguide.event.application.UpdateEventCommand;
import eu.uniche.iguide.event.application.UpdateEventUseCase;
import eu.uniche.iguide.event.domain.Event;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.workingcopy.application.LiveResource;
import eu.uniche.iguide.workingcopy.application.LiveRowLock;
import eu.uniche.iguide.workingcopy.application.WorkingCopyHandler;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The working copy of an ACTIVE Event: the Event's update body without {@code version}, plus its occurrences
 * (an entry with the {@code id} of an existing occurrence updates it) and its media (DECISIONS.md D-027).
 */
@Component
public class EventWorkingCopyHandler implements WorkingCopyHandler<EventWorkingCopyHandler.State> {

    public record State(String slug, UUID placeId, UUID poiId, UUID routeId, String bookingUrl, String internalName,
                        boolean featured, Map<String, EventTranslationBody> translations,
                        List<EventOccurrenceUseCase.WantedOccurrence> occurrences,
                        List<ResourceAssetUseCase.Media> media) {
    }

    private final LiveRowLock lock;
    private final EventRules rules;
    private final UpdateEventUseCase updateEvent;
    private final EventOccurrenceUseCase occurrences;
    private final ResourceAssetUseCase resourceAssets;

    public EventWorkingCopyHandler(LiveRowLock lock, EventRules rules, UpdateEventUseCase updateEvent,
                                   EventOccurrenceUseCase occurrences, ResourceAssetUseCase resourceAssets) {
        this.lock = lock;
        this.rules = rules;
        this.updateEvent = updateEvent;
        this.occurrences = occurrences;
        this.resourceAssets = resourceAssets;
    }

    @Override
    public WorkingCopyResourceType type() {
        return WorkingCopyResourceType.EVENT;
    }

    @Override
    public Class<State> stateType() {
        return State.class;
    }

    @Override
    public LiveResource lock(UUID projectId, UUID resourceId) {
        Event event = lock.lock(Event.class, projectId, resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("No such event in this project."));
        return new LiveResource(event.getStatus(), event.getVersion());
    }

    @Override
    public void write(UUID projectId, UUID resourceId, UnichePrincipal principal, State state) {
        Event event = rules.requireEvent(projectId, resourceId);
        updateEvent.write(projectId, event, principal, new UpdateEventCommand(null, state.slug(), state.placeId(),
                state.poiId(), state.routeId(), state.bookingUrl(), state.internalName(), state.featured(),
                EventRequests.inputs(state.translations())));
        occurrences.replaceOccurrences(projectId, event, state.occurrences());
        resourceAssets.replace(projectId, AssetResourceType.EVENT, resourceId, state.media());
    }

    @Override
    public Object render(UUID projectId, UUID resourceId, Instant workingCopyUpdatedAt) {
        return EventResponse.from(rules.view(rules.requireEvent(projectId, resourceId)), workingCopyUpdatedAt);
    }
}
