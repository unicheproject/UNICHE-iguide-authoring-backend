package eu.uniche.iguide.event.application;

import eu.uniche.iguide.event.domain.Event;
import eu.uniche.iguide.event.domain.EventTranslation;
import eu.uniche.iguide.event.infrastructure.EventRepository;
import eu.uniche.iguide.event.infrastructure.EventTranslationRepository;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ResourceRules;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Creates a DRAFT Event with its title in the default language and no dates, last in the Project's order. */
@Service
public class CreateEventUseCase {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final EventRules rules;
    private final EventRepository events;
    private final EventTranslationRepository translations;
    private final TransactionTemplate transactionTemplate;

    public CreateEventUseCase(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages, EventRules rules,
                              EventRepository events, EventTranslationRepository translations,
                              TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.rules = rules;
        this.events = events;
        this.translations = translations;
        this.transactionTemplate = transactionTemplate;
    }

    public EventView execute(UUID projectId, UnichePrincipal principal, String rawTitle, String rawSlug) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            String defaultLanguage = languages.requireDefaultLanguage(projectId);
            List<ErrorItem> errors = new ArrayList<>();
            String title = ResourceRules.trimToNull(rawTitle);
            if (title == null) {
                errors.add(new ErrorItem("title", "REQUIRED", "Is required in the default language."));
            }
            String slug = ResourceRules.trimToNull(rawSlug);
            if (slug != null) {
                rules.checkSlug(projectId, slug, null, errors);
            }
            ResourceRules.throwIfAny(errors, "The event is invalid.");
            if (slug == null) {
                slug = rules.generateSlug(projectId, title);
            }
            Event event = events.save(Event.draft(projectId, slug, events.findMaxSortOrder(projectId) + 1,
                    principal.subject(), Instant.now()));
            EventTranslation translation = EventTranslation.of(projectId, event.getId(), defaultLanguage);
            translation.write(title, null, null, null);
            translations.save(translation);
            return rules.view(event);
        });
    }
}
