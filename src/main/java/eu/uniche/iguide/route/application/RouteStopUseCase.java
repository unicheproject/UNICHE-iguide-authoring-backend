package eu.uniche.iguide.route.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.domain.RouteStop;
import eu.uniche.iguide.route.domain.RouteStopTranslation;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
import eu.uniche.iguide.route.infrastructure.RouteStopRepository;
import eu.uniche.iguide.workingcopy.application.PatchWorkingCopyUseCase;
import eu.uniche.iguide.workingcopy.application.PatchWorkingCopyUseCase.Patched;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The stops of a Route, a child collection ({@code 07} section 14: {@code .../routes/{id}/stops},
 * {@code .../stops/{stopId}}, {@code .../stops:order}). Positions stay 0-based and contiguous: a new stop
 * goes last, and removing one closes the gap. On a DRAFT Route the rows are written and every write counts as a
 * change of the Route; on an ACTIVE one the change goes into the Route's working copy, started from the live
 * Route when it has none, and visitors see it after apply (DECISIONS.md D-045). The answer has the same shape
 * either way. An ARCHIVED Route is refused.
 */
@Service
public class RouteStopUseCase {

    private static final String STOPS = "stops";

    private final ProjectAccessGateway accessGateway;
    private final RouteRules rules;
    private final RouteStopWriter writer;
    private final RouteRepository routes;
    private final RouteStopRepository stops;
    private final PatchWorkingCopyUseCase patch;
    private final TransactionTemplate transactionTemplate;

    public RouteStopUseCase(ProjectAccessGateway accessGateway, RouteRules rules, RouteStopWriter writer,
                            RouteRepository routes, RouteStopRepository stops, PatchWorkingCopyUseCase patch,
                            TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.writer = writer;
        this.routes = routes;
        this.stops = stops;
        this.patch = patch;
        this.transactionTemplate = transactionTemplate;
    }

    /** One stop of a working-copy payload; {@code id} names an existing stop to keep, or is {@code null}. */
    public record WantedStop(UUID id, UUID poiId, Map<String, StopTranslationInput> translations) {
    }

    /**
     * A stop as the endpoints answer it: a row of a DRAFT Route, or an entry of an ACTIVE Route's working copy,
     * whose id there becomes a row's at apply only for stops that already existed.
     */
    public record StopData(UUID id, UUID poiId, int position, Map<String, StopTranslationInput> translations) {

        static StopData of(StopView view) {
            Map<String, StopTranslationInput> texts = new HashMap<>();
            for (RouteStopTranslation t : view.translations()) {
                texts.put(t.getLanguageCode(), new StopTranslationInput(t.getTitleOverride(), t.getDescription(),
                        t.getNavigationInstructions(), t.getNarrative()));
            }
            return new StopData(view.stop().getId(), view.stop().getPoiId(), view.stop().getPosition(), texts);
        }
    }

    public StopData add(UUID projectId, UUID routeId, UnichePrincipal principal, UUID poiId,
                        Map<String, StopTranslationInput> translations) {
        accessGateway.requireAccess(projectId, principal);
        StopData added = transactionTemplate.execute(status -> {
            Route route = draftOrNull(projectId, routeId);
            if (route == null) {
                return null;
            }
            RouteStop stop = writer.addIn(projectId, routeId, poiId, translations);
            touch(route, principal);
            return StopData.of(rules.stopView(stop));
        });
        if (added != null) {
            return added;
        }
        return patch.execute(projectId, WorkingCopyResourceType.ROUTE, routeId, principal, payload -> {
            List<Map<String, Object>> items = PatchWorkingCopyUseCase.children(payload, STOPS);
            Map<String, StopTranslationInput> texts = translations == null ? Map.of() : translations;
            WantedStop stop = new WantedStop(UUID.randomUUID(), poiId, texts);
            items.add(patch.entry(stop));
            int position = items.size() - 1;
            return new Patched<>(new StopData(stop.id(), poiId, position, texts), at(position));
        });
    }

    public StopData update(UUID projectId, UUID routeId, UUID stopId, UnichePrincipal principal, UUID poiId,
                           Map<String, StopTranslationInput> translations) {
        accessGateway.requireAccess(projectId, principal);
        StopData updated = transactionTemplate.execute(status -> {
            Route route = draftOrNull(projectId, routeId);
            if (route == null) {
                return null;
            }
            RouteStop stop = writer.updateIn(projectId, routeId, rules.requireStop(projectId, routeId, stopId),
                    poiId, translations);
            touch(route, principal);
            return StopData.of(rules.stopView(stop));
        });
        if (updated != null) {
            return updated;
        }
        return patch.execute(projectId, WorkingCopyResourceType.ROUTE, routeId, principal, payload -> {
            List<Map<String, Object>> items = PatchWorkingCopyUseCase.children(payload, STOPS);
            int position = items.indexOf(pending(items, stopId));
            WantedStop current = patch.read(items.get(position), WantedStop.class);
            // Languages not sent keep their texts, as on a DRAFT Route.
            Map<String, StopTranslationInput> texts = new HashMap<>(
                    current.translations() == null ? Map.of() : current.translations());
            if (translations != null) {
                texts.putAll(translations);
            }
            items.set(position, patch.entry(new WantedStop(stopId, poiId, texts)));
            return new Patched<>(new StopData(stopId, poiId, position, texts), at(position));
        });
    }

    public void remove(UUID projectId, UUID routeId, UUID stopId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        boolean removed = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            Route route = draftOrNull(projectId, routeId);
            if (route == null) {
                return false;
            }
            stops.delete(rules.requireStop(projectId, routeId, stopId));
            stops.flush();
            List<RouteStop> remaining = rules.stopsOf(projectId, routeId);
            for (int i = 0; i < remaining.size(); i++) {
                remaining.get(i).moveTo(i);
            }
            stops.saveAll(remaining);
            touch(route, principal);
            return true;
        }));
        if (!removed) {
            patch.execute(projectId, WorkingCopyResourceType.ROUTE, routeId, principal, payload -> {
                List<Map<String, Object>> items = PatchWorkingCopyUseCase.children(payload, STOPS);
                items.remove(pending(items, stopId));
                return new Patched<>(true, STOPS);
            });
        }
    }

    /** {@code PUT .../stops:order}: every stop id of the Route, in the new order. */
    public List<StopData> reorder(UUID projectId, UUID routeId, UnichePrincipal principal, List<UUID> orderedIds) {
        accessGateway.requireAccess(projectId, principal);
        List<UUID> ids = orderedIds == null ? List.of() : orderedIds;
        List<StopData> reordered = transactionTemplate.execute(status -> {
            Route route = draftOrNull(projectId, routeId);
            if (route == null) {
                return null;
            }
            List<RouteStop> current = rules.stopsOf(projectId, routeId);
            Map<UUID, RouteStop> byId = current.stream().collect(Collectors.toMap(RouteStop::getId, s -> s));
            requireEvery(ids, byId.keySet().stream().toList());
            for (int i = 0; i < ids.size(); i++) {
                byId.get(ids.get(i)).moveTo(i);
            }
            stops.saveAllAndFlush(current);
            touch(route, principal);
            return rules.view(route).stops().stream().map(StopData::of).toList();
        });
        if (reordered != null) {
            return reordered;
        }
        return patch.execute(projectId, WorkingCopyResourceType.ROUTE, routeId, principal, payload -> {
            List<Map<String, Object>> items = PatchWorkingCopyUseCase.children(payload, STOPS);
            requireEvery(ids, items.stream().map(item -> UUID.fromString(String.valueOf(item.get("id")))).toList());
            List<Map<String, Object>> ordered = ids.stream()
                    .map(id -> PatchWorkingCopyUseCase.child(items, id).orElseThrow()).toList();
            items.clear();
            items.addAll(ordered);
            List<StopData> result = new ArrayList<>();
            for (int i = 0; i < ordered.size(); i++) {
                WantedStop stop = patch.read(ordered.get(i), WantedStop.class);
                result.add(new StopData(stop.id(), stop.poiId(), i,
                        stop.translations() == null ? Map.of() : stop.translations()));
            }
            return new Patched<>(result, STOPS);
        });
    }

    private static void requireEvery(List<UUID> ids, List<UUID> existing) {
        if (ids.size() != existing.size() || !new HashSet<>(ids).equals(new HashSet<>(existing))) {
            throw new ValidationFailedException("The order must list every stop of the route exactly once.",
                    List.of(new ErrorItem("ids", "INCOMPLETE_ORDER", "List every stop of this route exactly once.")));
        }
    }

    /** The Route when it is a DRAFT, {@code null} when it is ACTIVE; an ARCHIVED one is refused. */
    private Route draftOrNull(UUID projectId, UUID routeId) {
        Route route = rules.requireRoute(projectId, routeId);
        if (route.getStatus() == ResourceStatus.ACTIVE) {
            return null;
        }
        ResourceRules.requireDraft(route.getStatus(), "route");
        return route;
    }

    private static Map<String, Object> pending(List<Map<String, Object>> items, UUID stopId) {
        return PatchWorkingCopyUseCase.child(items, stopId)
                .orElseThrow(() -> new ResourceNotFoundException("No such stop on this route."));
    }

    private static String at(int position) {
        return STOPS + "[" + position + "]";
    }

    private void touch(Route route, UnichePrincipal principal) {
        route.touch(principal.subject(), Instant.now());
        routes.saveAndFlush(route);
    }
}
