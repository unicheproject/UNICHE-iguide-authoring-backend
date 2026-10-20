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
import eu.uniche.iguide.resource.application.ResourceRules;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The occurrences of a DRAFT Event, a child collection ({@code 07} section 14:
 * {@code .../events/{id}/occurrences}, {@code .../occurrences/{occurrenceId}}). They are ordered by their
 * start, so there is no {@code :order}. A missing status means {@code SCHEDULED}.
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

    public EventOccurrence add(UUID projectId, UUID eventId, UnichePrincipal principal, Instant startsAt,
                               Instant endsAt, OccurrenceStatus status) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(tx -> {
            Event event = requireDraftEvent(projectId, eventId);
            if (occurrences.countByProjectIdAndEventId(projectId, eventId) >= EventRules.MAX_OCCURRENCES) {
                throw new ValidationFailedException("The event has too many occurrences.", List.of(new ErrorItem(
                        "occurrences", "TOO_MANY", "At most " + EventRules.MAX_OCCURRENCES + " per event.")));
            }
            List<ErrorItem> errors = new ArrayList<>();
            EventRules.checkTimes(startsAt, endsAt, errors);
            ResourceRules.throwIfAny(errors, "The occurrence is invalid.");
            EventOccurrence occurrence = occurrences.saveAndFlush(EventOccurrence.of(projectId, eventId, startsAt,
                    endsAt, status == null ? OccurrenceStatus.SCHEDULED : status));
            touch(event, principal);
            return occurrence;
        });
    }

    public EventOccurrence update(UUID projectId, UUID eventId, UUID occurrenceId, UnichePrincipal principal,
                                  Instant startsAt, Instant endsAt, OccurrenceStatus status) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(tx -> {
            Event event = requireDraftEvent(projectId, eventId);
            EventOccurrence occurrence = rules.requireOccurrence(projectId, eventId, occurrenceId);
            List<ErrorItem> errors = new ArrayList<>();
            EventRules.checkTimes(startsAt, endsAt, errors);
            ResourceRules.throwIfAny(errors, "The occurrence is invalid.");
            occurrence.reschedule(startsAt, endsAt, status == null ? OccurrenceStatus.SCHEDULED : status);
            occurrences.saveAndFlush(occurrence);
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
