package eu.uniche.iguide.readiness.application;

import eu.uniche.iguide.category.domain.Category;
import eu.uniche.iguide.category.domain.CategoryTranslation;
import eu.uniche.iguide.category.infrastructure.CategoryRepository;
import eu.uniche.iguide.category.infrastructure.CategoryTranslationRepository;
import eu.uniche.iguide.event.domain.Event;
import eu.uniche.iguide.event.domain.EventTranslation;
import eu.uniche.iguide.event.infrastructure.EventRepository;
import eu.uniche.iguide.event.infrastructure.EventTranslationRepository;
import eu.uniche.iguide.floorplan.domain.FloorPlan;
import eu.uniche.iguide.floorplan.infrastructure.FloorPlanRepository;
import eu.uniche.iguide.guide.domain.Guide;
import eu.uniche.iguide.guide.domain.GuideTranslation;
import eu.uniche.iguide.guide.infrastructure.GuideRepository;
import eu.uniche.iguide.guide.infrastructure.GuideTranslationRepository;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.language.domain.ProjectLanguage;
import eu.uniche.iguide.map.domain.GeographicMap;
import eu.uniche.iguide.map.infrastructure.GeographicMapRepository;
import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.domain.PlaceTranslation;
import eu.uniche.iguide.place.infrastructure.PlaceRepository;
import eu.uniche.iguide.place.infrastructure.PlaceTranslationRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import eu.uniche.iguide.poi.domain.PoiTranslation;
import eu.uniche.iguide.poi.infrastructure.PoiTranslationRepository;
import eu.uniche.iguide.poi.infrastructure.PointOfInterestRepository;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.domain.RouteTranslation;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
import eu.uniche.iguide.route.infrastructure.RouteTranslationRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code GET .../readiness} ({@code 07} section 14, DECISIONS.md D-033): computed on request from the live rows, never
 * cached. Every {@link ReadinessCheck} adds what it finds on {@code ACTIVE} Resources; the Project is activatable when
 * nothing blocks.
 */
@Service
public class GetReadinessQuery {

    /** The evaluation: whether nothing blocks, and the blocking findings and warnings. */
    public record Readiness(boolean activatable, List<ReadinessIssue> blocking, List<ReadinessIssue> warnings) {
    }

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final List<ReadinessCheck> checks;
    private final TransactionTemplate transactionTemplate;
    private final PlaceRepository places;
    private final PlaceTranslationRepository placeTexts;
    private final PointOfInterestRepository pois;
    private final PoiTranslationRepository poiTexts;
    private final RouteRepository routes;
    private final RouteTranslationRepository routeTexts;
    private final GuideRepository guides;
    private final GuideTranslationRepository guideTexts;
    private final EventRepository events;
    private final EventTranslationRepository eventTexts;
    private final CategoryRepository categories;
    private final CategoryTranslationRepository categoryTexts;
    private final GeographicMapRepository maps;
    private final FloorPlanRepository plans;

    public GetReadinessQuery(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages,
                             List<ReadinessCheck> checks, TransactionTemplate transactionTemplate,
                             PlaceRepository places, PlaceTranslationRepository placeTexts,
                             PointOfInterestRepository pois, PoiTranslationRepository poiTexts,
                             RouteRepository routes, RouteTranslationRepository routeTexts,
                             GuideRepository guides, GuideTranslationRepository guideTexts,
                             EventRepository events, EventTranslationRepository eventTexts,
                             CategoryRepository categories, CategoryTranslationRepository categoryTexts,
                             GeographicMapRepository maps, FloorPlanRepository plans) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.checks = checks;
        this.transactionTemplate = transactionTemplate;
        this.places = places;
        this.placeTexts = placeTexts;
        this.pois = pois;
        this.poiTexts = poiTexts;
        this.routes = routes;
        this.routeTexts = routeTexts;
        this.guides = guides;
        this.guideTexts = guideTexts;
        this.events = events;
        this.eventTexts = eventTexts;
        this.categories = categories;
        this.categoryTexts = categoryTexts;
        this.maps = maps;
        this.plans = plans;
    }

    public Readiness execute(UUID projectId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return evaluate(projectId);
        });
    }

    /**
     * The evaluation itself, inside the caller's transaction and without an access check: the caller has checked
     * access and may have written rows the evaluation must see, such as an apply (DECISIONS.md D-037).
     */
    public Readiness evaluate(UUID projectId) {
        List<String> codes = languages.enabledLanguages(projectId).stream()
                .map(ProjectLanguage::getLanguageCode).toList();
        String defaultLanguage = languages.enabledLanguages(projectId).stream()
                .filter(ProjectLanguage::isDefaultLanguage).map(ProjectLanguage::getLanguageCode)
                .findFirst().orElse(null);
        ReadinessContext context = new ReadinessContext(projectId, defaultLanguage, codes,
                index(projectId, defaultLanguage));
        ReadinessReport report = new ReadinessReport();
        checks.forEach(check -> check.check(context, report));
        List<ReadinessIssue> blocking = report.blocking();
        return new Readiness(blocking.isEmpty(), blocking, report.warnings());
    }

    private ResourceIndex index(UUID projectId, String language) {
        ResourceIndex index = new ResourceIndex();
        List<Place> placeRows = places.findByProjectId(projectId);
        Map<UUID, String> placeTitles = titles(placeTexts.findByProjectIdAndPlaceIdIn(projectId, ids(placeRows,
                Place::getId)), language, PlaceTranslation::getLanguageCode, PlaceTranslation::getPlaceId,
                PlaceTranslation::getTitle);
        placeRows.forEach(p -> index.put("PLACE", p.getId(), p.getStatus(), placeTitles.get(p.getId())));

        List<PointOfInterest> poiRows = pois.findByProjectId(projectId);
        Map<UUID, String> poiTitles = titles(poiTexts.findByProjectIdAndPoiIdIn(projectId, ids(poiRows,
                PointOfInterest::getId)), language, PoiTranslation::getLanguageCode, PoiTranslation::getPoiId,
                PoiTranslation::getTitle);
        poiRows.forEach(p -> index.put("POI", p.getId(), p.getStatus(), poiTitles.get(p.getId())));

        List<Route> routeRows = routes.findByProjectId(projectId);
        Map<UUID, String> routeTitles = titles(routeTexts.findByProjectIdAndRouteIdIn(projectId, ids(routeRows,
                Route::getId)), language, RouteTranslation::getLanguageCode, RouteTranslation::getRouteId,
                RouteTranslation::getTitle);
        routeRows.forEach(r -> index.put("ROUTE", r.getId(), r.getStatus(), routeTitles.get(r.getId())));

        List<Guide> guideRows = guides.findByProjectId(projectId);
        Map<UUID, String> guideTitles = titles(guideTexts.findByProjectIdAndGuideIdIn(projectId, ids(guideRows,
                Guide::getId)), language, GuideTranslation::getLanguageCode, GuideTranslation::getGuideId,
                GuideTranslation::getTitle);
        guideRows.forEach(g -> index.put("GUIDE", g.getId(), g.getStatus(), guideTitles.get(g.getId())));

        List<Event> eventRows = events.findByProjectId(projectId);
        Map<UUID, String> eventTitles = titles(eventTexts.findByProjectIdAndEventIdIn(projectId, ids(eventRows,
                Event::getId)), language, EventTranslation::getLanguageCode, EventTranslation::getEventId,
                EventTranslation::getTitle);
        eventRows.forEach(e -> index.put("EVENT", e.getId(), e.getStatus(), eventTitles.get(e.getId())));

        List<Category> categoryRows = categories.findByProjectId(projectId);
        Map<UUID, String> categoryNames = titles(categoryTexts.findByProjectIdAndCategoryIdIn(projectId,
                ids(categoryRows, Category::getId)), language, CategoryTranslation::getLanguageCode,
                CategoryTranslation::getCategoryId, CategoryTranslation::getName);
        categoryRows.forEach(c -> index.put("CATEGORY", c.getId(), c.getStatus(), categoryNames.get(c.getId())));

        for (GeographicMap map : maps.findAll((root, query, cb) -> cb.equal(root.get("projectId"), projectId))) {
            index.put("MAP", map.getId(), map.getStatus(), map.getInternalName());
        }
        for (FloorPlan plan : plans.findByProjectId(projectId)) {
            index.put("FLOOR_PLAN", plan.getId(), plan.getStatus(), plan.getInternalName());
        }
        return index;
    }

    private static <T> List<UUID> ids(List<T> rows, Function<T, UUID> id) {
        return rows.stream().map(id).toList();
    }

    /** The default-language text of each Resource, where there is one. */
    private static <T> Map<UUID, String> titles(List<T> rows, String language, Function<T, String> languageCode,
                                                Function<T, UUID> owner, Function<T, String> text) {
        return rows.stream()
                .filter(row -> Objects.equals(languageCode.apply(row), language) && text.apply(row) != null)
                .collect(Collectors.toMap(owner, text, (a, b) -> a));
    }
}
