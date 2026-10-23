package eu.uniche.iguide.event.application;

import eu.uniche.iguide.event.domain.Event;
import eu.uniche.iguide.event.domain.EventTranslation;
import eu.uniche.iguide.event.infrastructure.EventRepository;
import eu.uniche.iguide.event.infrastructure.EventTranslationRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ResourceRules;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Saves a DRAFT Event's own fields and texts. An ACTIVE Event is edited through its working copy, whose save and
 * apply reuse {@link #write} (DECISIONS.md D-027).
 */
@Service
public class UpdateEventUseCase {

    private final ProjectAccessGateway accessGateway;
    private final EventRules rules;
    private final EventRepository events;
    private final EventTranslationRepository translations;
    private final TransactionTemplate transactionTemplate;

    public UpdateEventUseCase(ProjectAccessGateway accessGateway, EventRules rules, EventRepository events,
                              EventTranslationRepository translations, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.events = events;
        this.translations = translations;
        this.transactionTemplate = transactionTemplate;
    }

    public EventView execute(UUID projectId, UUID eventId, UnichePrincipal principal, UpdateEventCommand command) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Event event = rules.requireEvent(projectId, eventId);
            ResourceRules.requireCurrentVersion(event.getVersion(), command.version(), "event");
            ResourceRules.requireDraft(event.getStatus(), "event");
            return write(projectId, event, principal, command);
        });
    }

    /**
     * Validates and writes the Event's own fields and texts, inside the caller's transaction, without a status or
     * version check: the DRAFT save above and the working copy of an ACTIVE Event share it.
     */
    public EventView write(UUID projectId, Event event, UnichePrincipal principal, UpdateEventCommand command) {
        UUID eventId = event.getId();
        List<ErrorItem> errors = new ArrayList<>();
        Map<String, EventTranslationInput> requested =
                command.translations() == null ? Map.of() : command.translations();
        rules.checkLanguages(projectId, requested.keySet(), errors);
        rules.checkLinks(projectId, command.placeId(), command.poiId(), command.routeId(), errors);
        String bookingUrl = EventRules.checkBookingUrl(command.bookingUrl(), errors);
        rules.checkSlug(projectId, command.slug(), eventId, errors);
        ResourceRules.throwIfAny(errors, "The event is invalid.");

        event.edit(command.slug(), command.placeId(), command.poiId(), command.routeId(), bookingUrl,
                ResourceRules.trimToNull(command.internalName()), command.featured(), principal.subject(),
                Instant.now());
        Map<String, EventTranslation> existing = translations.findByProjectIdAndEventId(projectId, eventId).stream()
                .collect(Collectors.toMap(EventTranslation::getLanguageCode, Function.identity()));
        List<EventTranslation> toSave = new ArrayList<>();
        requested.forEach((code, input) -> {
            EventTranslation row = existing.getOrDefault(code, EventTranslation.of(projectId, eventId, code));
            row.write(ResourceRules.trimToNull(input.title()), ResourceRules.trimToNull(input.summary()),
                    ResourceRules.trimToNull(input.description()), ResourceRules.trimToNull(input.locationText()));
            toSave.add(row);
        });
        translations.saveAll(toSave);
        return rules.view(events.saveAndFlush(event));
    }
}
