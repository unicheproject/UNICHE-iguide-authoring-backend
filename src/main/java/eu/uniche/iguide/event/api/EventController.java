package eu.uniche.iguide.event.api;

import eu.uniche.iguide.event.application.ChangeEventStatusUseCase;
import eu.uniche.iguide.event.application.CreateEventUseCase;
import eu.uniche.iguide.event.application.EventOccurrenceUseCase;
import eu.uniche.iguide.event.application.EventView;
import eu.uniche.iguide.event.application.GetEventQuery;
import eu.uniche.iguide.event.application.ListEventsQuery;
import eu.uniche.iguide.event.application.ReorderEventsUseCase;
import eu.uniche.iguide.event.application.UpdateEventCommand;
import eu.uniche.iguide.event.application.UpdateEventUseCase;
import eu.uniche.iguide.event.domain.EventOccurrence;
import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.platform.web.PageResponse;
import eu.uniche.iguide.resource.api.ChangeStatusRequest;
import eu.uniche.iguide.resource.api.OrderRequest;
import eu.uniche.iguide.resource.application.ListParams;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.workingcopy.application.WorkingCopyStore;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Events of a Project, their occurrences, and the Project's event calendar ({@code 07} section 14,
 * {@code 08} Milestone 9). The class mapping stops at the Project so that {@code events:order} sits
 * beside {@code events}.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class EventController {

    private final ListEventsQuery listEvents;
    private final GetEventQuery getEvent;
    private final CreateEventUseCase createEvent;
    private final UpdateEventUseCase updateEvent;
    private final ChangeEventStatusUseCase changeStatus;
    private final ReorderEventsUseCase reorderEvents;
    private final EventOccurrenceUseCase occurrences;
    private final WorkingCopyStore workingCopies;

    public EventController(ListEventsQuery listEvents, GetEventQuery getEvent, CreateEventUseCase createEvent,
                           UpdateEventUseCase updateEvent, ChangeEventStatusUseCase changeStatus,
                           ReorderEventsUseCase reorderEvents, EventOccurrenceUseCase occurrences,
                           WorkingCopyStore workingCopies) {
        this.listEvents = listEvents;
        this.getEvent = getEvent;
        this.createEvent = createEvent;
        this.updateEvent = updateEvent;
        this.changeStatus = changeStatus;
        this.reorderEvents = reorderEvents;
        this.occurrences = occurrences;
        this.workingCopies = workingCopies;
    }

    @GetMapping("/events")
    public PageResponse<EventResponse> list(@PathVariable UUID projectId,
                                            @RequestParam(required = false) ResourceStatus status,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) String language,
                                            @RequestParam(required = false) Boolean featured,
                                            @RequestParam(required = false) String sort,
                                            @RequestParam(required = false) Boolean pending,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        Page<EventView> result = listEvents.execute(projectId, CurrentPrincipal.require(), new ListParams(status, q,
                language, featured, Boolean.TRUE.equals(pending), sort, page, size));
        Map<UUID, Instant> copies = workingCopies.updatedAt(projectId, WorkingCopyResourceType.EVENT,
                result.map(view -> view.event().getId()).getContent());
        return PageResponse.of(result, view -> EventResponse.from(view, copies.get(view.event().getId())));
    }

    /** Occurrences of every Event of the Project overlapping {@code [from, to)}, at most 400 days. */
    @GetMapping("/events/occurrences")
    public List<EventResponse.Occurrence> calendar(
            @PathVariable UUID projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return getEvent.calendar(projectId, CurrentPrincipal.require(), from, to).stream()
                .map(EventResponse.Occurrence::from).toList();
    }

    @PostMapping("/events")
    public ResponseEntity<EventResponse> create(@PathVariable UUID projectId,
                                                @RequestBody EventRequests.Create request) {
        EventView created = createEvent.execute(projectId, CurrentPrincipal.require(), request.title(), request.slug());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/events/" + created.event().getId()))
                .body(EventResponse.from(created));
    }

    @GetMapping("/events/{eventId}")
    public EventResponse get(@PathVariable UUID projectId, @PathVariable UUID eventId) {
        return EventResponse.from(getEvent.execute(projectId, eventId, CurrentPrincipal.require()),
                workingCopies.updatedAt(projectId, WorkingCopyResourceType.EVENT, eventId));
    }

    @PutMapping("/events/{eventId}")
    public EventResponse update(@PathVariable UUID projectId, @PathVariable UUID eventId,
                                @Valid @RequestBody EventRequests.Update request) {
        return EventResponse.from(updateEvent.execute(projectId, eventId, CurrentPrincipal.require(),
                new UpdateEventCommand(request.version(), request.slug(), request.placeId(), request.poiId(),
                        request.routeId(), request.bookingUrl(), request.internalName(), request.featured(),
                        request.translationInputs())));
    }

    @PostMapping("/events/{eventId}/status")
    public EventResponse changeStatus(@PathVariable UUID projectId, @PathVariable UUID eventId,
                                      @Valid @RequestBody ChangeStatusRequest request) {
        return EventResponse.from(changeStatus.execute(projectId, eventId, CurrentPrincipal.require(),
                request.targetStatus(), request.version()));
    }

    @GetMapping("/events/{eventId}/references")
    public List<ResourceReference> references(@PathVariable UUID projectId, @PathVariable UUID eventId) {
        return getEvent.references(projectId, eventId, CurrentPrincipal.require());
    }

    @PutMapping("/events:order")
    public ResponseEntity<Void> reorder(@PathVariable UUID projectId, @Valid @RequestBody OrderRequest request) {
        reorderEvents.execute(projectId, CurrentPrincipal.require(), request.ids());
        return ResponseEntity.noContent().build();
    }

    // --- Occurrences -----------------------------------------------------------------------------

    @GetMapping("/events/{eventId}/occurrences")
    public List<EventResponse.Occurrence> listOccurrences(@PathVariable UUID projectId, @PathVariable UUID eventId) {
        return getEvent.execute(projectId, eventId, CurrentPrincipal.require()).occurrences().stream()
                .map(EventResponse.Occurrence::from).toList();
    }

    @PostMapping("/events/{eventId}/occurrences")
    public ResponseEntity<EventResponse.Occurrence> addOccurrence(@PathVariable UUID projectId,
                                                                  @PathVariable UUID eventId,
                                                                  @RequestBody EventRequests.Occurrence request) {
        EventOccurrence created = occurrences.add(projectId, eventId, CurrentPrincipal.require(), request.startsAt(),
                request.endsAt(), request.status());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/events/" + eventId
                + "/occurrences/" + created.getId())).body(EventResponse.Occurrence.from(created));
    }

    @PutMapping("/events/{eventId}/occurrences/{occurrenceId}")
    public EventResponse.Occurrence updateOccurrence(@PathVariable UUID projectId, @PathVariable UUID eventId,
                                                     @PathVariable UUID occurrenceId,
                                                     @RequestBody EventRequests.Occurrence request) {
        return EventResponse.Occurrence.from(occurrences.update(projectId, eventId, occurrenceId,
                CurrentPrincipal.require(), request.startsAt(), request.endsAt(), request.status()));
    }

    @DeleteMapping("/events/{eventId}/occurrences/{occurrenceId}")
    public ResponseEntity<Void> removeOccurrence(@PathVariable UUID projectId, @PathVariable UUID eventId,
                                                 @PathVariable UUID occurrenceId) {
        occurrences.remove(projectId, eventId, occurrenceId, CurrentPrincipal.require());
        return ResponseEntity.noContent().build();
    }
}
