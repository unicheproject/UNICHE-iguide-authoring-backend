package eu.uniche.iguide.route.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ErrorPaths;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.domain.RouteStop;
import eu.uniche.iguide.route.domain.RouteStopTranslation;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
import eu.uniche.iguide.route.infrastructure.RouteStopRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
 * goes last, and removing one closes the gap. Every write counts as a change of the Route. An ACTIVE Route
 * changes its stops through its working copy, whose apply calls {@link #replaceStops} (DECISIONS.md D-027).
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
            RouteStop stop = addIn(projectId, routeId, poiId, translations);
            touch(route, principal);
            return rules.stopView(stop);
        });
    }

    public StopView update(UUID projectId, UUID routeId, UUID stopId, UnichePrincipal principal, UUID poiId,
                           Map<String, StopTranslationInput> translations) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Route route = requireDraftRoute(projectId, routeId);
            RouteStop stop = updateIn(projectId, routeId, rules.requireStop(projectId, routeId, stopId), poiId,
                    translations);
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

    /** One stop of a working-copy payload; {@code id} names an existing stop to keep, or is {@code null}. */
    public record WantedStop(UUID id, UUID poiId, Map<String, StopTranslationInput> translations) {
    }

    /**
     * Makes the Route's stops exactly {@code wanted}, in that order, inside the caller's transaction and without a
     * status check: the apply of a working copy (DECISIONS.md D-027). A stop is kept, and updated, when its id is
     * wanted with the same Point of Interest; every other stop is removed, and the remaining wanted ones are
     * created, a stop whose Point of Interest changed keeping its texts. Positions follow the payload, which the
     * deferred position constraint allows. Errors are reported at {@code stops[i]}.
     */
    public void replaceStops(UUID projectId, UUID routeId, List<WantedStop> wanted) {
        List<WantedStop> desired = wanted == null ? List.of() : wanted;
        Map<UUID, RouteStop> current = rules.stopsOf(projectId, routeId).stream()
                .collect(Collectors.toMap(RouteStop::getId, s -> s));
        Set<UUID> kept = new HashSet<>();
        for (WantedStop stop : desired) {
            RouteStop existing = stop.id() == null ? null : current.get(stop.id());
            if (existing != null && existing.getPoiId().equals(stop.poiId()) && !kept.contains(stop.id())) {
                kept.add(stop.id());
            }
        }
        Map<UUID, Map<String, StopTranslationInput>> carried = new HashMap<>();
        List<RouteStop> removed = current.values().stream().filter(s -> !kept.contains(s.getId())).toList();
        for (RouteStop stop : removed) {
            carried.put(stop.getId(), texts(stop));
        }
        stops.deleteAll(removed);
        stops.flush();

        List<RouteStop> ordered = new ArrayList<>();
        Set<UUID> placed = new HashSet<>();
        for (int i = 0; i < desired.size(); i++) {
            WantedStop stop = desired.get(i);
            boolean keep = stop.id() != null && kept.contains(stop.id()) && placed.add(stop.id());
            ordered.add(ErrorPaths.within("stops[" + i + "]", () -> keep
                    ? updateIn(projectId, routeId, current.get(stop.id()), stop.poiId(), stop.translations())
                    : addIn(projectId, routeId, stop.poiId(), merged(carried.get(stop.id()), stop.translations()))));
        }
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).moveTo(i);
        }
        stops.saveAllAndFlush(ordered);
    }

    private RouteStop addIn(UUID projectId, UUID routeId, UUID poiId, Map<String, StopTranslationInput> translations) {
        List<ErrorItem> errors = new ArrayList<>();
        rules.checkStopPoi(projectId, routeId, poiId, null, errors);
        rules.checkLanguages(projectId, keys(translations), "", errors);
        ResourceRules.throwIfAny(errors, "The stop is invalid.");
        RouteStop stop = stops.saveAndFlush(RouteStop.of(projectId, routeId, poiId,
                rules.stopsOf(projectId, routeId).size()));
        rules.saveStopTranslations(stop, translations);
        return stop;
    }

    private RouteStop updateIn(UUID projectId, UUID routeId, RouteStop stop, UUID poiId,
                               Map<String, StopTranslationInput> translations) {
        List<ErrorItem> errors = new ArrayList<>();
        rules.checkStopPoi(projectId, routeId, poiId, stop.getId(), errors);
        rules.checkLanguages(projectId, keys(translations), "", errors);
        ResourceRules.throwIfAny(errors, "The stop is invalid.");
        stop.bindTo(poiId);
        stops.saveAndFlush(stop);
        rules.saveStopTranslations(stop, translations);
        return stop;
    }

    private Map<String, StopTranslationInput> texts(RouteStop stop) {
        return rules.stopView(stop).translations().stream().collect(Collectors.toMap(
                RouteStopTranslation::getLanguageCode, t -> new StopTranslationInput(t.getTitleOverride(),
                        t.getDescription(), t.getNavigationInstructions(), t.getNarrative())));
    }

    /** The texts of a replaced stop, overwritten language by language by the wanted ones. */
    private static Map<String, StopTranslationInput> merged(Map<String, StopTranslationInput> previous,
                                                           Map<String, StopTranslationInput> wanted) {
        Map<String, StopTranslationInput> result = new HashMap<>(previous == null ? Map.of() : previous);
        if (wanted != null) {
            result.putAll(wanted);
        }
        return result;
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
