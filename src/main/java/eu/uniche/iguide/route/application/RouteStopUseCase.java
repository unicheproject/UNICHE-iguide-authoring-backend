package eu.uniche.iguide.route.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.domain.RouteStop;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
import eu.uniche.iguide.route.infrastructure.RouteStopRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The stops of a DRAFT Route, a child collection ({@code 07} section 14: {@code .../routes/{id}/stops},
 * {@code .../stops/{stopId}}, {@code .../stops:order}). Positions stay 0-based and contiguous: a new stop
 * goes last, and removing one closes the gap. Every write counts as a change of the Route.
 */
@Service
public class RouteStopUseCase {

    private final ProjectAccessGateway accessGateway;
    private final RouteRules rules;
    private final RouteRepository routes;
    private final RouteStopRepository stops;
    private final TransactionTemplate transactionTemplate;

    public RouteStopUseCase(ProjectAccessGateway accessGateway, RouteRules rules, RouteRepository routes,
                            RouteStopRepository stops, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.routes = routes;
        this.stops = stops;
        this.transactionTemplate = transactionTemplate;
    }

    public StopView add(UUID projectId, UUID routeId, UnichePrincipal principal, UUID poiId,
                        Map<String, StopTranslationInput> translations) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Route route = requireDraftRoute(projectId, routeId);
            List<ErrorItem> errors = new ArrayList<>();
            rules.checkStopPoi(projectId, routeId, poiId, null, errors);
            rules.checkLanguages(projectId, keys(translations), "", errors);
            ResourceRules.throwIfAny(errors, "The stop is invalid.");
            RouteStop stop = stops.saveAndFlush(RouteStop.of(projectId, routeId, poiId,
                    rules.stopsOf(projectId, routeId).size()));
            rules.saveStopTranslations(stop, translations);
            touch(route, principal);
            return rules.stopView(stop);
        });
    }

    public StopView update(UUID projectId, UUID routeId, UUID stopId, UnichePrincipal principal, UUID poiId,
                           Map<String, StopTranslationInput> translations) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Route route = requireDraftRoute(projectId, routeId);
            RouteStop stop = rules.requireStop(projectId, routeId, stopId);
            List<ErrorItem> errors = new ArrayList<>();
            rules.checkStopPoi(projectId, routeId, poiId, stopId, errors);
            rules.checkLanguages(projectId, keys(translations), "", errors);
            ResourceRules.throwIfAny(errors, "The stop is invalid.");
            stop.bindTo(poiId);
            stops.saveAndFlush(stop);
            rules.saveStopTranslations(stop, translations);
            touch(route, principal);
            return rules.stopView(stop);
        });
    }

    public void remove(UUID projectId, UUID routeId, UUID stopId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        transactionTemplate.executeWithoutResult(status -> {
            Route route = requireDraftRoute(projectId, routeId);
            stops.delete(rules.requireStop(projectId, routeId, stopId));
            stops.flush();
            List<RouteStop> remaining = rules.stopsOf(projectId, routeId);
            for (int i = 0; i < remaining.size(); i++) {
                remaining.get(i).moveTo(i);
            }
            stops.saveAll(remaining);
            touch(route, principal);
        });
    }

    /** {@code PUT .../stops:order}: every stop id of the Route, in the new order. */
    public List<StopView> reorder(UUID projectId, UUID routeId, UnichePrincipal principal, List<UUID> orderedIds) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Route route = requireDraftRoute(projectId, routeId);
            List<RouteStop> current = rules.stopsOf(projectId, routeId);
            Map<UUID, RouteStop> byId = current.stream().collect(Collectors.toMap(RouteStop::getId, s -> s));
            List<UUID> ids = orderedIds == null ? List.of() : orderedIds;
            if (ids.size() != current.size() || !new HashSet<>(ids).equals(byId.keySet())) {
                throw new ValidationFailedException("The order must list every stop of the route exactly once.",
                        List.of(new ErrorItem("ids", "INCOMPLETE_ORDER", "List every stop of this route exactly once.")));
            }
            for (int i = 0; i < ids.size(); i++) {
                byId.get(ids.get(i)).moveTo(i);
            }
            stops.saveAllAndFlush(current);
            touch(route, principal);
            return rules.view(route).stops();
        });
    }

    private Route requireDraftRoute(UUID projectId, UUID routeId) {
        Route route = rules.requireRoute(projectId, routeId);
        ResourceRules.requireDraft(route.getStatus(), "route");
        return route;
    }

    private void touch(Route route, UnichePrincipal principal) {
        route.touch(principal.subject(), Instant.now());
        routes.saveAndFlush(route);
    }

    private static Set<String> keys(Map<String, StopTranslationInput> translations) {
        return translations == null ? Set.of() : translations.keySet();
    }
}
