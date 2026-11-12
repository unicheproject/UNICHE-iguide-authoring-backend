package eu.uniche.iguide.route.application;

import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ErrorPaths;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.route.domain.RouteStop;
import eu.uniche.iguide.route.domain.RouteStopTranslation;
import eu.uniche.iguide.route.infrastructure.RouteStopRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The rules and row writes of a Route's stops, shared by the stop endpoints and the apply of a working copy.
 * Every method runs inside the caller's transaction, without a status check.
 */
@Component
public class RouteStopWriter {

    private final RouteRules rules;
    private final RouteStopRepository stops;

    public RouteStopWriter(RouteRules rules, RouteStopRepository stops) {
        this.rules = rules;
        this.stops = stops;
    }

    /**
     * Makes the Route's stops exactly {@code wanted}, in that order, inside the caller's transaction and without a
     * status check: the apply of a working copy (DECISIONS.md D-027). A stop is kept, and updated, when its id is
     * wanted with the same Point of Interest; every other stop is removed, and the remaining wanted ones are
     * created, a stop whose Point of Interest changed keeping its texts. Positions follow the payload, which the
     * deferred position constraint allows. Errors are reported at {@code stops[i]}.
     */
    public void replaceStops(UUID projectId, UUID routeId, List<RouteStopUseCase.WantedStop> wanted) {
        List<RouteStopUseCase.WantedStop> desired = wanted == null ? List.of() : wanted;
        Map<UUID, RouteStop> current = rules.stopsOf(projectId, routeId).stream()
                .collect(Collectors.toMap(RouteStop::getId, s -> s));
        Set<UUID> kept = new HashSet<>();
        for (RouteStopUseCase.WantedStop stop : desired) {
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
            RouteStopUseCase.WantedStop stop = desired.get(i);
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

    RouteStop addIn(UUID projectId, UUID routeId, UUID poiId, Map<String, StopTranslationInput> translations) {
        List<ErrorItem> errors = new ArrayList<>();
        rules.checkStopPoi(projectId, routeId, poiId, null, errors);
        rules.checkLanguages(projectId, keys(translations), "", errors);
        ResourceRules.throwIfAny(errors, "The stop is invalid.");
        RouteStop stop = stops.saveAndFlush(RouteStop.of(projectId, routeId, poiId,
                rules.stopsOf(projectId, routeId).size()));
        rules.saveStopTranslations(stop, translations);
        return stop;
    }

    RouteStop updateIn(UUID projectId, UUID routeId, RouteStop stop, UUID poiId,
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

    private static Set<String> keys(Map<String, StopTranslationInput> translations) {
        return translations == null ? Set.of() : translations.keySet();
    }
}
