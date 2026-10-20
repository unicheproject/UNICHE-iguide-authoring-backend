package eu.uniche.iguide.place.application;

import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.infrastructure.PlaceRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceInUseException;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies one lifecycle transition ({@code 03} section 1). Activation needs a title in the default
 * language. Deactivation and archival are refused with {@code RESOURCE_IN_USE} while a map or floor plan
 * links or places the Place. Sub-Places and, later, Points of Interest inside it do not block
 * (DECISIONS.md D-019).
 */
@Service
public class ChangePlaceStatusUseCase {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final PlaceRules rules;
    private final PlaceRepository places;
    private final TransactionTemplate transactionTemplate;

    public ChangePlaceStatusUseCase(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages,
                                    PlaceRules rules, PlaceRepository places, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.rules = rules;
        this.places = places;
        this.transactionTemplate = transactionTemplate;
    }

    public PlaceView execute(UUID projectId, UUID placeId, UnichePrincipal principal, ResourceStatus target,
                             Long version) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Place place = rules.requirePlace(projectId, placeId);
            ResourceRules.requireCurrentVersion(place.getVersion(), version, "place");
            ResourceRules.requireTransition(place.getStatus(), target, "place");
            if (target == ResourceStatus.ACTIVE) {
                String defaultLanguage = languages.requireDefaultLanguage(projectId);
                boolean titled = rules.view(place).translations().stream()
                        .anyMatch(t -> t.getLanguageCode().equals(defaultLanguage) && t.hasTitle());
                if (!titled) {
                    throw new ValidationFailedException("Required fields are missing in the default language.",
                            List.of(new ErrorItem("translations." + defaultLanguage + ".title",
                                    "MISSING_REQUIRED_FIELDS", "Is required in the default language.")));
                }
            }
            if (place.getStatus().leavesVisitorsFor(target)) {
                List<ResourceReference> references = rules.referencesTo(projectId, placeId);
                if (!references.isEmpty()) {
                    throw new ResourceInUseException("The place is still used by other resources.", references);
                }
            }
            place.changeStatus(target, principal.subject(), Instant.now());
            return rules.view(places.saveAndFlush(place));
        });
    }
}
