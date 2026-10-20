package eu.uniche.iguide.place.application;

import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.domain.PlaceTranslation;
import eu.uniche.iguide.place.infrastructure.PlaceRepository;
import eu.uniche.iguide.place.infrastructure.PlaceTranslationRepository;
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

/** Creates a DRAFT Place with its title in the default language, last in the Project's order. */
@Service
public class CreatePlaceUseCase {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final PlaceRules rules;
    private final PlaceRepository places;
    private final PlaceTranslationRepository translations;
    private final TransactionTemplate transactionTemplate;

    public CreatePlaceUseCase(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages, PlaceRules rules,
                              PlaceRepository places, PlaceTranslationRepository translations,
                              TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.rules = rules;
        this.places = places;
        this.translations = translations;
        this.transactionTemplate = transactionTemplate;
    }

    public PlaceView execute(UUID projectId, UnichePrincipal principal, CreatePlaceCommand command) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            String defaultLanguage = languages.requireDefaultLanguage(projectId);
            List<ErrorItem> errors = new ArrayList<>();
            String title = ResourceRules.trimToNull(command.title());
            if (title == null) {
                errors.add(new ErrorItem("title", "REQUIRED", "Is required in the default language."));
            }
            if (command.type() == null) {
                errors.add(new ErrorItem("type", "REQUIRED", "Is required."));
            }
            rules.checkParent(projectId, command.parentPlaceId(), null, errors);
            String slug = ResourceRules.trimToNull(command.slug());
            if (slug != null) {
                rules.checkSlug(projectId, slug, null, errors);
            }
            ResourceRules.throwIfAny(errors, "The place is invalid.");
            if (slug == null) {
                slug = rules.generateSlug(projectId, title);
            }

            Place place = places.save(Place.draft(projectId, command.parentPlaceId(), slug, command.type(),
                    command.environment() != null ? command.environment() : command.type().defaultEnvironment(),
                    places.findMaxSortOrder(projectId) + 1, principal.subject(), Instant.now()));
            PlaceTranslation translation = PlaceTranslation.of(projectId, place.getId(), defaultLanguage);
            translation.write(title, null, null, null, null);
            translations.save(translation);
            return rules.view(place);
        });
    }
}
