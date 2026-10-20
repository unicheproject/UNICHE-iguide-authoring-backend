package eu.uniche.iguide.event.application;

import eu.uniche.iguide.event.domain.EventOccurrence;
import eu.uniche.iguide.event.infrastructure.EventOccurrenceRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.domain.ResourceReference;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Reads one Event, its occurrences, the rows that reference it, and the Project's calendar. */
@Service
public class GetEventQuery {

    static final Duration MAX_CALENDAR_RANGE = Duration.ofDays(400);

    private final ProjectAccessGateway accessGateway;
    private final EventRules rules;
    private final EventOccurrenceRepository occurrences;

    public GetEventQuery(ProjectAccessGateway accessGateway, EventRules rules, EventOccurrenceRepository occurrences) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.occurrences = occurrences;
    }

    public EventView execute(UUID projectId, UUID eventId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return rules.view(rules.requireEvent(projectId, eventId));
    }

    public List<ResourceReference> references(UUID projectId, UUID eventId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        rules.requireEvent(projectId, eventId);
        return rules.referencesTo(projectId, eventId);
    }

    /**
     * The occurrences of the Project's Events that overlap {@code [from, to)}, at most 400 days, in start
     * order ({@code 08} Milestone 9 "calendar queries by occurrence"), whatever the Event's status.
     */
    public List<EventOccurrence> calendar(UUID projectId, UnichePrincipal principal, Instant from, Instant to) {
        accessGateway.requireAccess(projectId, principal);
        if (from == null || to == null || !to.isAfter(from) || Duration.between(from, to).compareTo(MAX_CALENDAR_RANGE) > 0) {
            throw new ValidationFailedException("The date range is invalid.", List.of(new ErrorItem("to", "INVALID_RANGE",
                    "Give from and to, with to after from and at most 400 days later.")));
        }
        return occurrences.findOverlapping(projectId, from, to);
    }
}
