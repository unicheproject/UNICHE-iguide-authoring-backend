package eu.uniche.iguide.map.application;

import eu.uniche.iguide.category.domain.Category;
import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.category.infrastructure.CategoryRepository;
import eu.uniche.iguide.map.domain.GeographicMap;
import eu.uniche.iguide.map.domain.GeographicMap.MapView;
import eu.uniche.iguide.map.domain.MapVisibility;
import eu.uniche.iguide.map.infrastructure.GeographicMapRepository;
import eu.uniche.iguide.place.domain.PlaceEnvironment;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.spatial.application.SpatialRules;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Rules the map use cases share. Callers have already passed the Project access check. */
@Component
public class MapRules {

    private static final BigDecimal MAX_ZOOM = BigDecimal.valueOf(22);

    private final GeographicMapRepository maps;
    private final SpatialRules spatial;
    private final CategoryRepository categories;

    public MapRules(GeographicMapRepository maps, SpatialRules spatial, CategoryRepository categories) {
        this.maps = maps;
        this.spatial = spatial;
        this.categories = categories;
    }

    public GeographicMap requireMap(UUID projectId, UUID mapId) {
        return SpatialRules.require(maps.findByProjectIdAndId(projectId, mapId), "map");
    }

    /**
     * Checks the name, the initial view, the bounds (all four or none, and the view inside them), and the
     * Place link (an {@code OUTDOOR} Place without another map). Returns the view with a trimmed name.
     */
    public MapView checkView(UUID projectId, MapView view, UUID ownId, List<ErrorItem> errors) {
        String name = ResourceRules.requireName(view.internalName(), "internalName", errors);
        ResourceRules.checkLatitude(view.initialLatitude(), "initialLatitude", errors);
        ResourceRules.checkLongitude(view.initialLongitude(), "initialLongitude", errors);
        if (view.initialZoom() == null) {
            errors.add(new ErrorItem("initialZoom", "REQUIRED", "Is required."));
        } else if (view.initialZoom().signum() < 0 || view.initialZoom().compareTo(MAX_ZOOM) > 0) {
            errors.add(new ErrorItem("initialZoom", "OUT_OF_RANGE", "Must be between 0 and 22."));
        }
        checkBounds(view, errors);
        spatial.checkLinkedPlace(projectId, view.placeId(), PlaceEnvironment.OUTDOOR, errors);
        if (view.placeId() != null) {
            maps.findByProjectIdAndPlaceId(projectId, view.placeId())
                    .filter(other -> !other.getId().equals(ownId))
                    .ifPresent(other -> errors.add(new ErrorItem("placeId", "ALREADY_LINKED",
                            "The place already shows another map.")));
        }
        return new MapView(name, view.placeId(), view.initialLatitude(), view.initialLongitude(), view.initialZoom(),
                view.minLatitude(), view.minLongitude(), view.maxLatitude(), view.maxLongitude());
    }

    private static void checkBounds(MapView view, List<ErrorItem> errors) {
        long set = Stream.of(view.minLatitude(), view.minLongitude(), view.maxLatitude(), view.maxLongitude())
                .filter(v -> v != null).count();
        if (set == 0) {
            return;
        }
        if (set < 4) {
            errors.add(new ErrorItem("minLatitude", "INCOMPLETE", "Set all four bounds, or none."));
            return;
        }
        int before = errors.size();
        ResourceRules.checkLatitude(view.minLatitude(), "minLatitude", errors);
        ResourceRules.checkLatitude(view.maxLatitude(), "maxLatitude", errors);
        ResourceRules.checkLongitude(view.minLongitude(), "minLongitude", errors);
        ResourceRules.checkLongitude(view.maxLongitude(), "maxLongitude", errors);
        if (errors.size() > before) {
            return;
        }
        if (view.minLatitude().compareTo(view.maxLatitude()) >= 0
                || view.minLongitude().compareTo(view.maxLongitude()) >= 0) {
            errors.add(new ErrorItem("maxLatitude", "INVALID_BOUNDS", "The maximum must exceed the minimum."));
            return;
        }
        if (view.initialLatitude() != null && view.initialLongitude() != null
                && (view.initialLatitude().compareTo(view.minLatitude()) < 0
                || view.initialLatitude().compareTo(view.maxLatitude()) > 0
                || view.initialLongitude().compareTo(view.minLongitude()) < 0
                || view.initialLongitude().compareTo(view.maxLongitude()) > 0)) {
            errors.add(new ErrorItem("initialLatitude", "OUTSIDE_BOUNDS", "The initial view must lie inside the bounds."));
        }
    }

    /**
     * Each hidden Category must be a Category of this Project with the scope of its key ({@code 03} section 7:
     * {@code POI} hides POI Categories, {@code ROUTE} Route Categories).
     */
    public void checkVisibility(UUID projectId, MapVisibility visibility, List<ErrorItem> errors) {
        if (visibility == null || visibility.poi() == null || visibility.route() == null) {
            errors.add(new ErrorItem("visibility", "REQUIRED", "Give the visibility of POI and ROUTE."));
            return;
        }
        checkHidden(projectId, visibility.poi().hiddenCategoryIds(), CategoryScope.POI, "visibility.POI.hiddenCategoryIds", errors);
        checkHidden(projectId, visibility.route().hiddenCategoryIds(), CategoryScope.ROUTE,
                "visibility.ROUTE.hiddenCategoryIds", errors);
    }

    private void checkHidden(UUID projectId, List<UUID> ids, CategoryScope scope, String path, List<ErrorItem> errors) {
        if (ids.isEmpty()) {
            return;
        }
        Map<UUID, Category> found = categories.findByProjectIdAndIdIn(projectId, ids).stream()
                .collect(Collectors.toMap(Category::getId, c -> c));
        for (UUID id : ids) {
            Category category = found.get(id);
            if (category == null) {
                errors.add(new ErrorItem(path, "RESOURCE_NOT_FOUND", "No such category in this project."));
                return;
            }
            if (category.getScope() != scope) {
                errors.add(new ErrorItem(path, "SCOPE_MISMATCH", "The category applies to " + category.getScope().label() + "."));
                return;
            }
        }
    }
}
