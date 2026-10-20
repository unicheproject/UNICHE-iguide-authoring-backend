package eu.uniche.iguide.route.application;

import eu.uniche.iguide.asset.application.ResourceMedia;
import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.asset.domain.ResourceAsset;
import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.category.domain.ResourceCategory;
import eu.uniche.iguide.category.infrastructure.ResourceCategoryRepository;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.platform.web.DuplicateValueException;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import eu.uniche.iguide.poi.infrastructure.PointOfInterestRepository;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.application.SlugGenerator;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.domain.RouteStop;
import eu.uniche.iguide.route.domain.RouteStopTranslation;
import eu.uniche.iguide.route.domain.RouteTranslation;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
import eu.uniche.iguide.route.infrastructure.RouteStopRepository;
import eu.uniche.iguide.route.infrastructure.RouteStopTranslationRepository;
import eu.uniche.iguide.route.infrastructure.RouteTranslationRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Lookups and rules the Route use cases share. Every method takes the Project explicitly; callers have
 * already passed the Project access check.
 */
@Component
public class RouteRules {

    public static final int MAX_DURATION_MINUTES = 10080;
    public static final int MAX_DISTANCE_METERS = 1_000_000;
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private final RouteRepository routes;
    private final RouteTranslationRepository translations;
    private final RouteStopRepository stops;
    private final RouteStopTranslationRepository stopTranslations;
    private final PointOfInterestRepository pois;
    private final ProjectLanguageLookup languages;
    private final ResourceCategoryRepository categories;
    private final ResourceMedia media;

    public RouteRules(RouteRepository routes, RouteTranslationRepository translations, RouteStopRepository stops,
                      RouteStopTranslationRepository stopTranslations, PointOfInterestRepository pois,
                      ProjectLanguageLookup languages, ResourceCategoryRepository categories,
                      ResourceMedia media) {
        this.routes = routes;
        this.translations = translations;
        this.stops = stops;
        this.stopTranslations = stopTranslations;
        this.pois = pois;
        this.languages = languages;
        this.categories = categories;
        this.media = media;
    }

    public Route requireRoute(UUID projectId, UUID routeId) {
        return routes.findByProjectIdAndId(projectId, routeId)
                .orElseThrow(() -> new ResourceNotFoundException("No such route in this project."));
    }

    public RouteStop requireStop(UUID projectId, UUID routeId, UUID stopId) {
        return stops.findByProjectIdAndRouteIdAndId(projectId, routeId, stopId)
                .orElseThrow(() -> new ResourceNotFoundException("No such stop on this route."));
    }

    public List<RouteStop> stopsOf(UUID projectId, UUID routeId) {
        return stops.findByProjectIdAndRouteIdOrderByPositionAsc(projectId, routeId);
    }

    public RouteView view(Route route) {
        return views(route.getProjectId(), List.of(route)).get(0);
    }

    /** Views of several Routes of one Project, loaded with one query per table. */
    public List<RouteView> views(UUID projectId, List<Route> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = list.stream().map(Route::getId).toList();
        Map<UUID, List<RouteTranslation>> texts = translations.findByProjectIdAndRouteIdIn(projectId, ids).stream()
                .collect(Collectors.groupingBy(RouteTranslation::getRouteId));
        List<RouteStop> allStops = stops.findByProjectIdAndRouteIdInOrderByPositionAsc(projectId, ids);
        Map<UUID, List<RouteStopTranslation>> stopTexts = allStops.isEmpty() ? Map.of()
                : stopTranslations.findByProjectIdAndRouteStopIdIn(projectId,
                        allStops.stream().map(RouteStop::getId).toList()).stream()
                        .collect(Collectors.groupingBy(RouteStopTranslation::getRouteStopId));
        Map<UUID, PointOfInterest> poiById = poisOf(projectId, allStops);
        Map<UUID, List<RouteStop>> byRoute = allStops.stream().collect(Collectors.groupingBy(RouteStop::getRouteId));
        Map<UUID, List<UUID>> categoryIds = categories.findByProjectIdAndResourceTypeAndResourceIdIn(projectId,
                        CategoryScope.ROUTE, ids).stream()
                .collect(Collectors.groupingBy(ResourceCategory::getResourceId,
                        Collectors.mapping(ResourceCategory::getCategoryId, Collectors.toList())));
        Map<UUID, List<ResourceAsset>> mediaByRoute = media.of(projectId, AssetResourceType.ROUTE, ids);
        return list.stream().map(route -> {
            List<RouteStop> own = byRoute.getOrDefault(route.getId(), List.of());
            List<StopView> stopViews = own.stream()
                    .map(stop -> new StopView(stop, stopTexts.getOrDefault(stop.getId(), List.of())))
                    .toList();
            return new RouteView(route, texts.getOrDefault(route.getId(), List.of()), stopViews,
                    computeDistance(own, poiById, route.isLoop()), categoryIds.getOrDefault(route.getId(), List.of()),
                    mediaByRoute.getOrDefault(route.getId(), List.of()));
        }).toList();
    }

    public StopView stopView(RouteStop stop) {
        return new StopView(stop, stopTranslations.findByProjectIdAndRouteStopIdIn(stop.getProjectId(),
                List.of(stop.getId())));
    }

    public Map<UUID, PointOfInterest> poisOf(UUID projectId, Collection<RouteStop> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        return pois.findByProjectIdAndIdIn(projectId, list.stream().map(RouteStop::getPoiId).distinct().toList())
                .stream().collect(Collectors.toMap(PointOfInterest::getId, Function.identity()));
    }

    /**
     * The path length through the stops' coordinates in order, with a closing segment for a loop of
     * three or more points ({@code 03} section 9). Stops whose POI has no coordinates are skipped; fewer
     * than two points give null.
     */
    static Integer computeDistance(List<RouteStop> ordered, Map<UUID, PointOfInterest> poiById, boolean loop) {
        List<double[]> points = new ArrayList<>();
        for (RouteStop stop : ordered) {
            PointOfInterest poi = poiById.get(stop.getPoiId());
            if (poi != null && poi.getLatitude() != null && poi.getLongitude() != null) {
                points.add(new double[] {poi.getLatitude().doubleValue(), poi.getLongitude().doubleValue()});
            }
        }
        if (points.size() < 2) {
            return null;
        }
        double meters = 0;
        for (int i = 1; i < points.size(); i++) {
            meters += haversine(points.get(i - 1), points.get(i));
        }
        if (loop && points.size() > 2) {
            meters += haversine(points.get(points.size() - 1), points.get(0));
        }
        return (int) Math.round(meters);
    }

    private static double haversine(double[] a, double[] b) {
        double dLat = Math.toRadians(b[0] - a[0]);
        double dLng = Math.toRadians(b[1] - a[1]);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(a[0])) * Math.cos(Math.toRadians(b[0])) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(h));
    }

    /** Adds an error when the slug is malformed; throws {@code 409} when another Route uses it. */
    public void checkSlug(UUID projectId, String slug, UUID ownId, List<ErrorItem> errors) {
        if (!ResourceRules.checkSlugFormat(slug, errors)) {
            return;
        }
        boolean taken = ownId == null
                ? routes.existsByProjectIdAndSlug(projectId, slug)
                : routes.existsByProjectIdAndSlugAndIdNot(projectId, slug, ownId);
        if (taken) {
            throw new DuplicateValueException("slug", "Another route in this project already uses this slug.");
        }
    }

    public String generateSlug(UUID projectId, String title) {
        return SlugGenerator.generate(title, "route", slug -> routes.existsByProjectIdAndSlug(projectId, slug));
    }

    /**
     * The Point of Interest of a stop: required, in this Project, not archived, and not already a stop of
     * the Route (DECISIONS.md D-021). A foreign POI is simply unknown.
     */
    public void checkStopPoi(UUID projectId, UUID routeId, UUID poiId, UUID ownStopId, List<ErrorItem> errors) {
        if (poiId == null) {
            errors.add(new ErrorItem("poiId", "REQUIRED", "Choose a point of interest."));
            return;
        }
        Optional<PointOfInterest> poi = pois.findByProjectIdAndId(projectId, poiId);
        if (poi.isEmpty()) {
            errors.add(new ErrorItem("poiId", "RESOURCE_NOT_FOUND", "No such point of interest in this project."));
        } else if (poi.get().getStatus() == ResourceStatus.ARCHIVED) {
            errors.add(new ErrorItem("poiId", "ARCHIVED_REFERENCE", "The point of interest is archived."));
        } else if (stopsOf(projectId, routeId).stream()
                .anyMatch(s -> s.getPoiId().equals(poiId) && !s.getId().equals(ownStopId))) {
            errors.add(new ErrorItem("poiId", "DUPLICATE_STOP", "The point of interest is already a stop of this route."));
        }
    }

    /** Every key must be an enabled language of the Project. */
    public void checkLanguages(UUID projectId, Set<String> codes, String prefix, List<ErrorItem> errors) {
        Set<String> enabled = languages.enabledCodes(projectId);
        codes.stream().filter(code -> !enabled.contains(code)).forEach(code -> errors.add(
                new ErrorItem(prefix + "translations." + code, "UNKNOWN_LANGUAGE", "Is not a language of this project.")));
    }

    public static void checkRange(Integer value, int max, String path, List<ErrorItem> errors) {
        if (value != null && (value < 1 || value > max)) {
            errors.add(new ErrorItem(path, "OUT_OF_RANGE", "Must be between 1 and " + max + "."));
        }
    }

    /** Writes the given languages of a stop; languages left out keep their text. */
    public void saveStopTranslations(RouteStop stop, Map<String, StopTranslationInput> requested) {
        if (requested == null || requested.isEmpty()) {
            return;
        }
        Map<String, RouteStopTranslation> existing = stopTranslations
                .findByProjectIdAndRouteStopIdIn(stop.getProjectId(), List.of(stop.getId())).stream()
                .collect(Collectors.toMap(RouteStopTranslation::getLanguageCode, Function.identity()));
        List<RouteStopTranslation> toSave = new ArrayList<>();
        requested.forEach((code, input) -> {
            RouteStopTranslation row = existing.getOrDefault(code,
                    RouteStopTranslation.of(stop.getProjectId(), stop.getId(), code));
            row.write(ResourceRules.trimToNull(input.titleOverride()), ResourceRules.trimToNull(input.description()),
                    ResourceRules.trimToNull(input.navigationInstructions()), ResourceRules.trimToNull(input.narrative()));
            toSave.add(row);
        });
        stopTranslations.saveAll(toSave);
    }
}
