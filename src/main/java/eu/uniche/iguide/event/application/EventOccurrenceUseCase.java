package eu.uniche.iguide.event.application;

import eu.uniche.iguide.event.domain.Event;
import eu.uniche.iguide.event.domain.EventOccurrence;
import eu.uniche.iguide.event.domain.OccurrenceStatus;
import eu.uniche.iguide.event.infrastructure.EventOccurrenceRepository;
import eu.uniche.iguide.event.infrastructure.EventRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ErrorPaths;
import eu.uniche.iguide.resource.application.ResourceRules;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The occurrences of an Event, a child collection ({@code 07} section 14: {@code .../events/{id}/occurrences},
 * {@code .../occurrences/{occurrenceId}}). These endpoints change a DRAFT Event; an ACTIVE one changes its
 * occurrences through its working copy, which calls {@link #replaceOccurrences} on apply (DECISIONS.md D-027).
 * They are ordered by their start, so there is no {@code :order}. A missing status means {@code SCHEDULED}.
 */
@Service
public class EventOccurrenceUseCase {

    private final ProjectAccessGateway accessGateway;
    private final EventRules rules;
    private final EventRepository events;
    private final EventOccurrenceRepository occurrences;
    private final TransactionTemplate transactionTemplate;

    public EventOccurrenceUseCase(ProjectAccessGateway accessGateway, EventRules rules, EventRepository events,
                                  EventOccurrenceRepository occurrences, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.events = events;
        this.occurrences = occurrences;
        this.transactionTemplate = transactionTemplate;
    }

    /** One wanted occurrence of a working-copy payload; {@code id} names an existing one to keep and update. */
    public record WantedOccurrence(UUID id, Instant startsAt, Instant endsAt, OccurrenceStatus status) {
    }

    public EventOccurrence add(UUID projectId, UUID eventId, UnichePrincipal principal, Instant startsAt,
                               Instant endsAt, OccurrenceStatus status) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(tx -> {
            Event event = requireDraftEvent(projectId, eventId);
            if (occurrences.countByProjectIdAndEventId(projectId, eventId) >= EventRules.MAX_OCCURRENCES) {
                throw tooMany();
            }
            EventOccurrence occurrence = create(projectId, eventId, startsAt, endsAt, status);
            touch(event, principal);
            return occurrence;
        });
    }

    public EventOccurrence update(UUID projectId, UUID eventId, UUID occurrenceId, UnichePrincipal principal,
                                  Instant startsAt, Instant endsAt, OccurrenceStatus status) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(tx -> {
            Event event = requireDraftEvent(projectId, eventId);
            EventOccurrence occurrence = reschedule(rules.requireOccurrence(projectId, eventId, occurrenceId),
                    startsAt, endsAt, status);
            touch(event, principal);
            return occurrence;
        });
    }

    public void remove(UUID projectId, UUID eventId, UUID occurrenceId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        transactionTemplate.executeWithoutResult(tx -> {
            Event event = requireDraftEvent(projectId, eventId);
            occurrences.delete(rules.requireOccurrence(projectId, eventId, occurrenceId));
            touch(event, principal);
        });
    }

    /**
     * Makes the Event's occurrences exactly {@code wanted}, inside the caller's transaction and without a status
     * check: the apply of a working copy. An entry whose {@code id} names an occurrence of this Event updates it;
     * the others are created; occurrences not named are deleted. Errors are reported at {@code occurrences[i]}.
     * The caller writes the Event itself, so it is not touched again here.
     */
    public void replaceOccurrences(UUID projectId, Event event, List<WantedOccurrence> wanted) {
        UUID eventId = event.getId();
        List<WantedOccurrence> desired = wanted == null ? List.of() : wanted;
        if (desired.size() > EventRules.MAX_OCCURRENCES) {
            throw tooMany();
        }
        Map<UUID, EventOccurrence> current = occurrences
                .findByProjectIdAndEventIdInOrderByStartsAtAsc(projectId, List.of(eventId)).stream()
                .collect(Collectors.toMap(EventOccurrence::getId, Function.identity()));
        Set<UUID> kept = new HashSet<>();
        for (WantedOccurrence occurrence : desired) {
            if (occurrence != null && occurrence.id() != null && current.containsKey(occurrence.id())) {
                kept.add(occurrence.id());
            }
        }
        List<EventOccurrence> removed = current.values().stream().filter(o -> !kept.contains(o.getId())).toList();
        occurrences.deleteAll(removed);
        occurrences.flush();

        Set<UUID> used = new HashSet<>();
        for (int i = 0; i < desired.size(); i++) {
            WantedOccurrence occurrence = desired.get(i);
            ErrorPaths.within("occurrences[" + i + "]", () -> {
                if (occurrence == null) {
                    throw new ValidationFailedException("The occurrence is invalid.",
                            List.of(new ErrorItem("", "REQUIRED", "Give the occurrence's times.")));
                }
                if (occurrence.id() != null && kept.contains(occurrence.id()) && used.add(occurrence.id())) {
                    reschedule(current.get(occurrence.id()), occurrence.startsAt(), occurrence.endsAt(),
                            occurrence.status());
                } else {
                    create(projectId, eventId, occurrence.startsAt(), occurrence.endsAt(), occurrence.status());
                }
            });
        }
    }

    private EventOccurrence create(UUID projectId, UUID eventId, Instant startsAt, Instant endsAt,
                                   OccurrenceStatus status) {
        checkTimes(startsAt, endsAt);
        return occurrences.saveAndFlush(EventOccurrence.of(projectId, eventId, startsAt, endsAt,
                status == null ? OccurrenceStatus.SCHEDULED : status));
    }

    private EventOccurrence reschedule(EventOccurrence occurrence, Instant startsAt, Instant endsAt,
                                       OccurrenceStatus status) {
        checkTimes(startsAt, endsAt);
        occurrence.reschedule(startsAt, endsAt, status == null ? OccurrenceStatus.SCHEDULED : status);
        return occurrences.saveAndFlush(occurrence);
    }

    private static void checkTimes(Instant startsAt, Instant endsAt) {
        List<ErrorItem> errors = new ArrayList<>();
        EventRules.checkTimes(startsAt, endsAt, errors);
        ResourceRules.throwIfAny(errors, "The occurrence is invalid.");
    }

    private static ValidationFailedException tooMany() {
        return new ValidationFailedException("The event has too many occurrences.", List.of(new ErrorItem(
                "occurrences", "TOO_MANY", "At most " + EventRules.MAX_OCCURRENCES + " per event.")));
    }

    private Event requireDraftEvent(UUID projectId, UUID eventId) {
        Event event = rules.requireEvent(projectId, eventId);
        ResourceRules.requireDraft(event.getStatus(), "event");
        return event;
    }

    private void touch(Event event, UnichePrincipal principal) {
        event.touch(principal.subject(), Instant.now());
        events.saveAndFlush(event);
    }
}
