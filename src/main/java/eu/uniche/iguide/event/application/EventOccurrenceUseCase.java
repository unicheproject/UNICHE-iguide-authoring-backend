package eu.uniche.iguide.event.application;

import eu.uniche.iguide.event.domain.Event;
import eu.uniche.iguide.event.domain.EventOccurrence;
import eu.uniche.iguide.event.domain.OccurrenceStatus;
import eu.uniche.iguide.event.infrastructure.EventOccurrenceRepository;
import eu.uniche.iguide.event.infrastructure.EventRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.workingcopy.application.PatchWorkingCopyUseCase;
import eu.uniche.iguide.workingcopy.application.PatchWorkingCopyUseCase.Patched;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The occurrences of an Event, a child collection ({@code 07} section 14: {@code .../events/{id}/occurrences},
 * {@code .../occurrences/{occurrenceId}}). On a DRAFT Event the rows are written; on an ACTIVE one the change goes
 * into the Event's working copy, started from the live Event when it has none, and visitors see it after apply
 * (DECISIONS.md D-045). The answer has the same shape either way; an ARCHIVED Event is refused. Occurrences are
 * ordered by their start, so there is no {@code :order}. A missing status means {@code SCHEDULED}.
 */
@Service
public class EventOccurrenceUseCase {

    private static final String OCCURRENCES = "occurrences";

    private final ProjectAccessGateway accessGateway;
    private final EventRules rules;
    private final EventOccurrenceWriter writer;
    private final EventRepository events;
    private final EventOccurrenceRepository occurrences;
    private final PatchWorkingCopyUseCase patch;
    private final TransactionTemplate transactionTemplate;

    public EventOccurrenceUseCase(ProjectAccessGateway accessGateway, EventRules rules, EventOccurrenceWriter writer,
                                  EventRepository events, EventOccurrenceRepository occurrences,
                                  PatchWorkingCopyUseCase patch, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.writer = writer;
        this.events = events;
        this.occurrences = occurrences;
        this.patch = patch;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * One occurrence: a wanted one of a working-copy payload, where {@code id} names an existing one to keep and
     * update, and what the endpoints answer. One added to a working copy has an id there until apply gives it a
     * row of its own.
     */
    public record WantedOccurrence(UUID id, Instant startsAt, Instant endsAt, OccurrenceStatus status) {

        static WantedOccurrence of(EventOccurrence o) {
            return new WantedOccurrence(o.getId(), o.getStartsAt(), o.getEndsAt(), o.getStatus());
        }
    }

    public WantedOccurrence add(UUID projectId, UUID eventId, UnichePrincipal principal, Instant startsAt,
                                Instant endsAt, OccurrenceStatus status) {
        accessGateway.requireAccess(projectId, principal);
        WantedOccurrence added = transactionTemplate.execute(tx -> {
            Event event = draftOrNull(projectId, eventId);
            if (event == null) {
                return null;
            }
            if (occurrences.countByProjectIdAndEventId(projectId, eventId) >= EventRules.MAX_OCCURRENCES) {
                throw EventOccurrenceWriter.tooMany();
            }
            EventOccurrence occurrence = writer.create(projectId, eventId, startsAt, endsAt, status);
            touch(event, principal);
            return WantedOccurrence.of(occurrence);
        });
        if (added != null) {
            return added;
        }
        return patch.execute(projectId, WorkingCopyResourceType.EVENT, eventId, principal, payload -> {
            List<Map<String, Object>> items = PatchWorkingCopyUseCase.children(payload, OCCURRENCES);
            if (items.size() >= EventRules.MAX_OCCURRENCES) {
                throw EventOccurrenceWriter.tooMany();
            }
            WantedOccurrence occurrence = new WantedOccurrence(UUID.randomUUID(), startsAt, endsAt, scheduled(status));
            items.add(patch.entry(occurrence));
            return new Patched<>(occurrence, at(items.size() - 1));
        });
    }

    public WantedOccurrence update(UUID projectId, UUID eventId, UUID occurrenceId, UnichePrincipal principal,
                                   Instant startsAt, Instant endsAt, OccurrenceStatus status) {
        accessGateway.requireAccess(projectId, principal);
        WantedOccurrence updated = transactionTemplate.execute(tx -> {
            Event event = draftOrNull(projectId, eventId);
            if (event == null) {
                return null;
            }
            EventOccurrence occurrence = writer.reschedule(rules.requireOccurrence(projectId, eventId, occurrenceId),
                    startsAt, endsAt, status);
            touch(event, principal);
            return WantedOccurrence.of(occurrence);
        });
        if (updated != null) {
            return updated;
        }
        return patch.execute(projectId, WorkingCopyResourceType.EVENT, eventId, principal, payload -> {
            List<Map<String, Object>> items = PatchWorkingCopyUseCase.children(payload, OCCURRENCES);
            int index = items.indexOf(pending(items, occurrenceId));
            WantedOccurrence occurrence = new WantedOccurrence(occurrenceId, startsAt, endsAt, scheduled(status));
            items.set(index, patch.entry(occurrence));
            return new Patched<>(occurrence, at(index));
        });
    }

    public void remove(UUID projectId, UUID eventId, UUID occurrenceId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        boolean removed = Boolean.TRUE.equals(transactionTemplate.execute(tx -> {
            Event event = draftOrNull(projectId, eventId);
            if (event == null) {
                return false;
            }
            occurrences.delete(rules.requireOccurrence(projectId, eventId, occurrenceId));
            touch(event, principal);
            return true;
        }));
        if (!removed) {
            patch.execute(projectId, WorkingCopyResourceType.EVENT, eventId, principal, payload -> {
                List<Map<String, Object>> items = PatchWorkingCopyUseCase.children(payload, OCCURRENCES);
                items.remove(pending(items, occurrenceId));
                return new Patched<>(true, OCCURRENCES);
            });
        }
    }

    private static OccurrenceStatus scheduled(OccurrenceStatus status) {
        return status == null ? OccurrenceStatus.SCHEDULED : status;
    }

    /** The Event when it is a DRAFT, {@code null} when it is ACTIVE; an ARCHIVED one is refused. */
    private Event draftOrNull(UUID projectId, UUID eventId) {
        Event event = rules.requireEvent(projectId, eventId);
        if (event.getStatus() == ResourceStatus.ACTIVE) {
            return null;
        }
        ResourceRules.requireDraft(event.getStatus(), "event");
        return event;
    }

    private static Map<String, Object> pending(List<Map<String, Object>> items, UUID occurrenceId) {
        return PatchWorkingCopyUseCase.child(items, occurrenceId)
                .orElseThrow(() -> new ResourceNotFoundException("No such occurrence of this event."));
    }

    private static String at(int index) {
        return OCCURRENCES + "[" + index + "]";
    }

    private void touch(Event event, UnichePrincipal principal) {
        event.touch(principal.subject(), Instant.now());
        events.saveAndFlush(event);
    }
}
