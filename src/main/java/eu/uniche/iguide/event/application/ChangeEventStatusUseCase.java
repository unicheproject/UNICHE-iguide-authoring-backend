package eu.uniche.iguide.event.application;

import eu.uniche.iguide.event.domain.Event;
import eu.uniche.iguide.event.infrastructure.EventRepository;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceInUseException;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.workingcopy.application.WorkingCopyStore;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies one lifecycle transition ({@code 03} section 1). Activation needs only the default-language
 * title; an Event without occurrences may be active. Deactivation and archival are refused with
 * {@code RESOURCE_IN_USE} while a non-archived Guide references the Event. Leaving ACTIVE discards the Event's
 * working copy ({@code 03} section 1).
 */
@Service
public class ChangeEventStatusUseCase {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final EventRules rules;
    private final EventRepository events;
    private final WorkingCopyStore workingCopies;
    private final TransactionTemplate transactionTemplate;

    public ChangeEventStatusUseCase(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages,
                                    EventRules rules, EventRepository events, WorkingCopyStore workingCopies,
                                    TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.rules = rules;
        this.events = events;
        this.workingCopies = workingCopies;
        this.transactionTemplate = transactionTemplate;
    }

    public EventView execute(UUID projectId, UUID eventId, UnichePrincipal principal, ResourceStatus target,
                             Long version) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Event event = rules.requireEvent(projectId, eventId);
            ResourceRules.requireCurrentVersion(event.getVersion(), version, "event");
            ResourceRules.requireTransition(event.getStatus(), target, "event");
            if (target == ResourceStatus.ACTIVE) {
                String defaultLanguage = languages.requireDefaultLanguage(projectId);
                boolean titled = rules.view(event).translations().stream()
                        .anyMatch(t -> t.getLanguageCode().equals(defaultLanguage) && t.hasTitle());
                if (!titled) {
                    throw new ValidationFailedException("Required fields are missing in the default language.",
                            List.of(new ErrorItem("translations." + defaultLanguage + ".title",
                                    "MISSING_REQUIRED_FIELDS", "Is required in the default language.")));
                }
            }
            if (event.getStatus().leavesVisitorsFor(target)) {
                List<ResourceReference> references = rules.referencesTo(projectId, eventId);
                if (!references.isEmpty()) {
                    throw new ResourceInUseException("The event is still used by other resources.", references);
                }
            }
            if (target != ResourceStatus.ACTIVE) {
                workingCopies.dropFor(projectId, WorkingCopyResourceType.EVENT, eventId);
            }
            event.changeStatus(target, principal.subject(), Instant.now());
            return rules.view(events.saveAndFlush(event));
        });
    }
}
