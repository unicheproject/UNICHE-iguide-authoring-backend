package eu.uniche.iguide.route.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.domain.RouteTranslation;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
import eu.uniche.iguide.route.infrastructure.RouteTranslationRepository;
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
 * Saves a DRAFT Route's own fields and translations. Its stops are written through their child collection
 * ({@code 07} section 14). An ACTIVE Route is edited through its working copy, whose save and apply reuse
 * {@link #write} (DECISIONS.md D-027).
 */
@Service
public class UpdateRouteUseCase {

    private final ProjectAccessGateway accessGateway;
    private final RouteRules rules;
    private final RouteRepository routes;
    private final RouteTranslationRepository translations;
    private final TransactionTemplate transactionTemplate;

    public UpdateRouteUseCase(ProjectAccessGateway accessGateway, RouteRules rules, RouteRepository routes,
                              RouteTranslationRepository translations, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.routes = routes;
        this.translations = translations;
        this.transactionTemplate = transactionTemplate;
    }

    public RouteView execute(UUID projectId, UUID routeId, UnichePrincipal principal, UpdateRouteCommand command) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Route route = rules.requireRoute(projectId, routeId);
            ResourceRules.requireCurrentVersion(route.getVersion(), command.version(), "route");
            ResourceRules.requireDraft(route.getStatus(), "route");
            return write(projectId, route, principal, command);
        });
    }

    /**
     * Validates and writes the Route's own fields and translations, inside the caller's transaction, without a
     * status or version check: the DRAFT save above and the working copy of an ACTIVE Route share it.
     */
    public RouteView write(UUID projectId, Route route, UnichePrincipal principal, UpdateRouteCommand command) {
        UUID routeId = route.getId();
        List<ErrorItem> errors = new ArrayList<>();
        if (command.type() == null) {
            errors.add(new ErrorItem("type", "REQUIRED", "Choose INDOOR or OUTDOOR."));
        }
        RouteRules.checkRange(command.estimatedDurationMinutes(), RouteRules.MAX_DURATION_MINUTES,
                "estimatedDurationMinutes", errors);
        RouteRules.checkRange(command.distanceMeters(), RouteRules.MAX_DISTANCE_METERS, "distanceMeters", errors);
        Map<String, RouteTranslationInput> requested =
                command.translations() == null ? Map.of() : command.translations();
        rules.checkLanguages(projectId, requested.keySet(), "", errors);
        rules.checkSlug(projectId, command.slug(), routeId, errors);
        ResourceRules.throwIfAny(errors, "The route is invalid.");

        route.edit(command.slug(), command.type(), command.travelMode(), command.difficulty(),
                command.estimatedDurationMinutes(), command.distanceMeters(), command.loop(),
                ResourceRules.trimToNull(command.internalName()), command.featured(), principal.subject(),
                Instant.now());
        saveTranslations(projectId, routeId, requested);
        return rules.view(routes.saveAndFlush(route));
    }

    private void saveTranslations(UUID projectId, UUID routeId, Map<String, RouteTranslationInput> requested) {
        Map<String, RouteTranslation> existing = translations.findByProjectIdAndRouteId(projectId, routeId).stream()
                .collect(Collectors.toMap(RouteTranslation::getLanguageCode, Function.identity()));
        List<RouteTranslation> toSave = new ArrayList<>();
        requested.forEach((code, input) -> {
            RouteTranslation row = existing.getOrDefault(code, RouteTranslation.of(projectId, routeId, code));
            row.write(ResourceRules.trimToNull(input.title()), ResourceRules.trimToNull(input.summary()),
                    ResourceRules.trimToNull(input.description()), ResourceRules.trimToNull(input.instructions()));
            toSave.add(row);
        });
        translations.saveAll(toSave);
    }
}
