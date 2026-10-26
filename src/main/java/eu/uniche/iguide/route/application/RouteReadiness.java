package eu.uniche.iguide.route.application;

import eu.uniche.iguide.readiness.application.ReadinessCheck;
import eu.uniche.iguide.readiness.application.ReadinessContext;
import eu.uniche.iguide.readiness.application.ReadinessReport;
import eu.uniche.iguide.readiness.application.Texts;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.domain.RouteStop;
import eu.uniche.iguide.route.domain.RouteTranslation;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
import eu.uniche.iguide.route.infrastructure.RouteStopRepository;
import eu.uniche.iguide.route.infrastructure.RouteTranslationRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Readiness of ACTIVE Routes ({@code 03} section 1; DECISIONS.md D-033): the title in the default language,
 * translations, stops whose Point of Interest is gone or archived ({@code DANGLING_REFERENCE}), and the two stop rules
 * the closed code lists lack (GAPS.md G-030, QUESTIONS.md Q-017): {@code ROUTE_WITHOUT_ACTIVE_STOP} blocks,
 * {@code ROUTE_FEW_STOPS} warns.
 */
@Component
public class RouteReadiness implements ReadinessCheck {

    static final List<String> FIELDS = List.of("title", "summary", "description", "instructions");

    private final RouteRepository routes;
    private final RouteTranslationRepository translations;
    private final RouteStopRepository stops;

    public RouteReadiness(RouteRepository routes, RouteTranslationRepository translations, RouteStopRepository stops) {
        this.routes = routes;
        this.translations = translations;
        this.stops = stops;
    }

    @Override
    public void check(ReadinessContext context, ReadinessReport report) {
        List<UUID> active = routes.findByProjectId(context.projectId()).stream()
                .filter(r -> r.getStatus() == ResourceStatus.ACTIVE).map(Route::getId).toList();
        if (active.isEmpty()) {
            return;
        }
        Map<UUID, Map<String, Map<String, String>>> texts = Texts.byOwner(
                translations.findByProjectIdAndRouteIdIn(context.projectId(), active), RouteTranslation::getRouteId,
                RouteTranslation::getLanguageCode, t -> Texts.of("title", t.getTitle(), "summary", t.getSummary(),
                        "description", t.getDescription(), "instructions", t.getInstructions()));
        Map<UUID, List<RouteStop>> stopsByRoute = stops
                .findByProjectIdAndRouteIdInOrderByPositionAsc(context.projectId(), active).stream()
                .collect(Collectors.groupingBy(RouteStop::getRouteId));
        for (UUID id : active) {
            String title = context.index().title("ROUTE", id);
            report.texts(context, "ROUTE", id, title, texts.getOrDefault(id, Map.of()), FIELDS, "title", List.of());
            List<RouteStop> own = stopsByRoute.getOrDefault(id, List.of());
            boolean activeStop = false;
            for (int i = 0; i < own.size(); i++) {
                UUID poiId = own.get(i).getPoiId();
                if (context.index().dangling("POI", poiId)) {
                    report.block("ROUTE", id, title, null, "stops[" + i + "].poiId", "DANGLING_REFERENCE");
                }
                activeStop |= context.index().status("POI", poiId) == ResourceStatus.ACTIVE;
            }
            if (!activeStop) {
                report.block("ROUTE", id, title, null, "stops", "ROUTE_WITHOUT_ACTIVE_STOP");
            }
            if (own.size() < 2) {
                report.warn("ROUTE", id, title, null, "stops", "ROUTE_FEW_STOPS");
            }
        }
    }
}
