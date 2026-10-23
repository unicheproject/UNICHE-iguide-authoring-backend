package eu.uniche.iguide.poi.application;

import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceInUseException;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import eu.uniche.iguide.poi.infrastructure.PointOfInterestRepository;
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
 * Applies one lifecycle transition ({@code 03} section 1). Activation needs a title in the default
 * language. Deactivation and archival are refused with {@code RESOURCE_IN_USE} while a map or floor plan
 * places the POI. Leaving ACTIVE discards its working copy ({@code 03} section 1).
 */
@Service
public class ChangePoiStatusUseCase {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final PoiRules rules;
    private final PointOfInterestRepository pois;
    private final WorkingCopyStore workingCopies;
    private final TransactionTemplate transactionTemplate;

    public ChangePoiStatusUseCase(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages, PoiRules rules,
                                  PointOfInterestRepository pois, WorkingCopyStore workingCopies,
                                  TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.rules = rules;
        this.pois = pois;
        this.workingCopies = workingCopies;
        this.transactionTemplate = transactionTemplate;
    }

    public PoiView execute(UUID projectId, UUID poiId, UnichePrincipal principal, ResourceStatus target, Long version) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            PointOfInterest poi = rules.requirePoi(projectId, poiId);
            ResourceRules.requireCurrentVersion(poi.getVersion(), version, "point of interest");
            ResourceRules.requireTransition(poi.getStatus(), target, "point of interest");
            if (target == ResourceStatus.ACTIVE) {
                String defaultLanguage = languages.requireDefaultLanguage(projectId);
                boolean titled = rules.view(poi).translations().stream()
                        .anyMatch(t -> t.getLanguageCode().equals(defaultLanguage) && t.hasTitle());
                if (!titled) {
                    throw new ValidationFailedException("Required fields are missing in the default language.",
                            List.of(new ErrorItem("translations." + defaultLanguage + ".title",
                                    "MISSING_REQUIRED_FIELDS", "Is required in the default language.")));
                }
            }
            if (poi.getStatus().leavesVisitorsFor(target)) {
                List<ResourceReference> references = rules.referencesTo(projectId, poiId);
                if (!references.isEmpty()) {
                    throw new ResourceInUseException("The point of interest is still used by other resources.",
                            references);
                }
            }
            if (target != ResourceStatus.ACTIVE) {
                workingCopies.dropFor(projectId, WorkingCopyResourceType.POI, poiId);
            }
            poi.changeStatus(target, principal.subject(), Instant.now());
            return rules.view(pois.saveAndFlush(poi));
        });
    }
}
