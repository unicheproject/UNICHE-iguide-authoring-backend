package eu.uniche.iguide.event.application;

import eu.uniche.iguide.event.domain.Event;
import eu.uniche.iguide.event.domain.EventOccurrence;
import eu.uniche.iguide.event.domain.OccurrenceStatus;
import eu.uniche.iguide.event.infrastructure.EventOccurrenceRepository;
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
import org.springframework.stereotype.Component;

/**
 * The rules and row writes of an Event's occurrences, shared by the occurrence endpoints and the apply of a
 * working copy. Every method runs inside the caller's transaction, without a status check.
 */
@Component
public class EventOccurrenceWriter {

    private final EventOccurrenceRepository occurrences;

    public EventOccurrenceWriter(EventOccurrenceRepository occurrences) {
        this.occurrences = occurrences;
    }

    /**
     * Makes the Event's occurrences exactly {@code wanted}, inside the caller's transaction and without a status
     * check: the apply of a working copy. An entry whose {@code id} names an occurrence of this Event updates it;
     * the others are created; occurrences not named are deleted. Errors are reported at {@code occurrences[i]}.
     * The caller writes the Event itself, so it is not touched again here.
     */
    public void replaceOccurrences(UUID projectId, Event event,
                                   List<EventOccurrenceUseCase.WantedOccurrence> wanted) {
        UUID eventId = event.getId();
        List<EventOccurrenceUseCase.WantedOccurrence> desired = wanted == null ? List.of() : wanted;
        if (desired.size() > EventRules.MAX_OCCURRENCES) {
            throw tooMany();
        }
        Map<UUID, EventOccurrence> current = occurrences
                .findByProjectIdAndEventIdInOrderByStartsAtAsc(projectId, List.of(eventId)).stream()
                .collect(Collectors.toMap(EventOccurrence::getId, Function.identity()));
        Set<UUID> kept = new HashSet<>();
        for (EventOccurrenceUseCase.WantedOccurrence occurrence : desired) {
            if (occurrence != null && occurrence.id() != null && current.containsKey(occurrence.id())) {
                kept.add(occurrence.id());
            }
        }
        List<EventOccurrence> removed = current.values().stream().filter(o -> !kept.contains(o.getId())).toList();
        occurrences.deleteAll(removed);
        occurrences.flush();

        Set<UUID> used = new HashSet<>();
        for (int i = 0; i < desired.size(); i++) {
            EventOccurrenceUseCase.WantedOccurrence occurrence = desired.get(i);
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

    EventOccurrence create(UUID projectId, UUID eventId, Instant startsAt, Instant endsAt,
                                   OccurrenceStatus status) {
        checkTimes(startsAt, endsAt);
        return occurrences.saveAndFlush(EventOccurrence.of(projectId, eventId, startsAt, endsAt,
                status == null ? OccurrenceStatus.SCHEDULED : status));
    }

    EventOccurrence reschedule(EventOccurrence occurrence, Instant startsAt, Instant endsAt,
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

    static ValidationFailedException tooMany() {
        return new ValidationFailedException("The event has too many occurrences.", List.of(new ErrorItem(
                "occurrences", "TOO_MANY", "At most " + EventRules.MAX_OCCURRENCES + " per event.")));
    }
}
