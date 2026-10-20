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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Saves a DRAFT Point of Interest in place. ACTIVE ones are edited through a working copy, which does not
 * exist yet, so only DRAFT ones are accepted here (DECISIONS.md D-019).
 */
@Service
public class UpdatePoiUseCase {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final PoiRules rules;
    private final PointOfInterestRepository pois;
    private final PoiTranslationRepository translations;
    private final TransactionTemplate transactionTemplate;

    public UpdatePoiUseCase(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages, PoiRules rules,
                            PointOfInterestRepository pois, PoiTranslationRepository translations,
                            TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.rules = rules;
        this.pois = pois;
        this.translations = translations;
        this.transactionTemplate = transactionTemplate;
    }

    public PoiView execute(UUID projectId, UUID poiId, UnichePrincipal principal, UpdatePoiCommand command) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            PointOfInterest poi = rules.requirePoi(projectId, poiId);
            ResourceRules.requireCurrentVersion(poi.getVersion(), command.version(), "point of interest");
            ResourceRules.requireDraft(poi.getStatus(), "point of interest");

            List<ErrorItem> errors = new ArrayList<>();
            ResourceRules.checkOptionalCoordinates(command.latitude(), command.longitude(), "", errors);
            PoiRules.checkVisitMinutes(command.estimatedVisitMinutes(), errors);
            Map<String, PoiTranslationInput> requested =
                    command.translations() == null ? Map.of() : command.translations();
            Set<String> enabled = languages.enabledCodes(projectId);
            requested.keySet().stream()
                    .filter(code -> !enabled.contains(code))
                    .forEach(code -> errors.add(new ErrorItem("translations." + code, "UNKNOWN_LANGUAGE",
                            "Is not a language of this project.")));
            rules.checkPlace(projectId, command.placeId(), errors);
            String code = ResourceRules.trimToNull(command.code());
            rules.checkCode(projectId, code, poiId, errors);
            rules.checkSlug(projectId, command.slug(), poiId, errors);
            ResourceRules.throwIfAny(errors, "The point of interest is invalid.");

            poi.edit(command.placeId(), command.slug(), code, ResourceRules.trimToNull(command.internalName()),
                    command.latitude(), command.longitude(), command.estimatedVisitMinutes(), command.featured(),
                    principal.subject(), Instant.now());
            saveTranslations(projectId, poiId, requested);
            return rules.view(pois.saveAndFlush(poi));
        });
    }

    private void saveTranslations(UUID projectId, UUID poiId, Map<String, PoiTranslationInput> requested) {
        Map<String, PoiTranslation> existing = translations.findByProjectIdAndPoiId(projectId, poiId).stream()
                .collect(Collectors.toMap(PoiTranslation::getLanguageCode, Function.identity()));
        List<PoiTranslation> toSave = new ArrayList<>();
        requested.forEach((code, input) -> {
            PoiTranslation row = existing.getOrDefault(code, PoiTranslation.of(projectId, poiId, code));
            row.write(ResourceRules.trimToNull(input.title()), ResourceRules.trimToNull(input.summary()),
                    ResourceRules.trimToNull(input.description()), ResourceRules.trimToNull(input.instructions()),
                    ResourceRules.trimToNull(input.accessibilityInformation()));
            toSave.add(row);
        });
        translations.saveAll(toSave);
    }
}
