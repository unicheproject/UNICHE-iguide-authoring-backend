package eu.uniche.iguide.place.application;

import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.domain.PlaceEnvironment;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Saves a DRAFT Place in place. An ACTIVE Place is edited through a working copy ({@code 03} section 1),
 * which does not exist yet, so only DRAFT Places are accepted here (DECISIONS.md D-019).
 */
@Service
public class UpdatePlaceUseCase {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final PlaceRules rules;
    private final PlaceRepository places;
    private final PlaceTranslationRepository translations;
    private final TransactionTemplate transactionTemplate;

    public UpdatePlaceUseCase(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages, PlaceRules rules,
                              PlaceRepository places, PlaceTranslationRepository translations,
                              TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.rules = rules;
        this.places = places;
        this.translations = translations;
        this.transactionTemplate = transactionTemplate;
    }

    public PlaceView execute(UUID projectId, UUID placeId, UnichePrincipal principal, UpdatePlaceCommand command) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Place place = rules.requirePlace(projectId, placeId);
            ResourceRules.requireCurrentVersion(place.getVersion(), command.version(), "place");
            ResourceRules.requireDraft(place.getStatus(), "place");

            List<ErrorItem> errors = new ArrayList<>();
            if (command.type() == null) {
                errors.add(new ErrorItem("type", "REQUIRED", "Is required."));
            }
            PlaceEnvironment environment = command.environment() != null ? command.environment() : place.getEnvironment();
            if (environment != place.getEnvironment() && rules.isLinked(projectId, placeId)) {
                errors.add(new ErrorItem("environment", "LINKED",
                        "Unlink the place's map or floor plans before changing its environment."));
            }
            ResourceRules.checkOptionalCoordinates(command.latitude(), command.longitude(), "", errors);
            Map<String, PlaceTranslationInput> requested =
                    command.translations() == null ? Map.of() : command.translations();
            Set<String> enabled = languages.enabledCodes(projectId);
            requested.keySet().stream()
                    .filter(code -> !enabled.contains(code))
                    .forEach(code -> errors.add(new ErrorItem("translations." + code, "UNKNOWN_LANGUAGE",
                            "Is not a language of this project.")));
            rules.checkParent(projectId, command.parentPlaceId(), placeId, errors);
            rules.checkSlug(projectId, command.slug(), placeId, errors);
            ResourceRules.throwIfAny(errors, "The place is invalid.");

            place.edit(command.parentPlaceId(), command.slug(), command.type(), environment,
                    ResourceRules.trimToNull(command.internalName()), command.latitude(), command.longitude(),
                    command.featured(), principal.subject(), Instant.now());
            saveTranslations(projectId, placeId, requested);
            return rules.view(places.saveAndFlush(place));
        });
    }

    private void saveTranslations(UUID projectId, UUID placeId, Map<String, PlaceTranslationInput> requested) {
        Map<String, PlaceTranslation> existing = translations.findByProjectIdAndPlaceId(projectId, placeId).stream()
                .collect(Collectors.toMap(PlaceTranslation::getLanguageCode, Function.identity()));
        List<PlaceTranslation> toSave = new ArrayList<>();
        requested.forEach((code, input) -> {
            PlaceTranslation row = existing.getOrDefault(code, PlaceTranslation.of(projectId, placeId, code));
            row.write(ResourceRules.trimToNull(input.title()), ResourceRules.trimToNull(input.summary()),
                    ResourceRules.trimToNull(input.description()), ResourceRules.trimToNull(input.visitorInformation()),
                    ResourceRules.trimToNull(input.accessibilityInformation()));
            toSave.add(row);
        });
        translations.saveAll(toSave);
    }
}
