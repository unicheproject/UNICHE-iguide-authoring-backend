package eu.uniche.iguide.poi.application;

import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.poi.domain.PoiTranslation;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import eu.uniche.iguide.poi.infrastructure.PoiTranslationRepository;
import eu.uniche.iguide.poi.infrastructure.PointOfInterestRepository;
import eu.uniche.iguide.resource.application.ResourceRules;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Creates a DRAFT Point of Interest with its title in the default language, last in the Project's order. */
@Service
public class CreatePoiUseCase {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final PoiRules rules;
    private final PointOfInterestRepository pois;
    private final PoiTranslationRepository translations;
    private final TransactionTemplate transactionTemplate;

    public CreatePoiUseCase(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages, PoiRules rules,
                            PointOfInterestRepository pois, PoiTranslationRepository translations,
                            TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.rules = rules;
        this.pois = pois;
        this.translations = translations;
        this.transactionTemplate = transactionTemplate;
    }

    public PoiView execute(UUID projectId, UnichePrincipal principal, CreatePoiCommand command) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            String defaultLanguage = languages.requireDefaultLanguage(projectId);
            List<ErrorItem> errors = new ArrayList<>();
            String title = ResourceRules.trimToNull(command.title());
            if (title == null) {
                errors.add(new ErrorItem("title", "REQUIRED", "Is required in the default language."));
            }
            rules.checkPlace(projectId, command.placeId(), errors);
            String code = ResourceRules.trimToNull(command.code());
            rules.checkCode(projectId, code, null, errors);
            String slug = ResourceRules.trimToNull(command.slug());
            if (slug != null) {
                rules.checkSlug(projectId, slug, null, errors);
            }
            ResourceRules.throwIfAny(errors, "The point of interest is invalid.");
            if (slug == null) {
                slug = rules.generateSlug(projectId, title);
            }

            PointOfInterest poi = pois.save(PointOfInterest.draft(projectId, command.placeId(), slug, code,
                    pois.findMaxSortOrder(projectId) + 1, principal.subject(), Instant.now()));
            PoiTranslation translation = PoiTranslation.of(projectId, poi.getId(), defaultLanguage);
            translation.write(title, null, null, null, null);
            translations.save(translation);
            return rules.view(poi);
        });
    }
}
