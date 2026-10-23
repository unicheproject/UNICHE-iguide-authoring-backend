package eu.uniche.iguide.route.api;

import eu.uniche.iguide.asset.api.AssetResponse;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.route.application.RouteView;
import eu.uniche.iguide.route.application.StopView;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.domain.RouteDifficulty;
import eu.uniche.iguide.route.domain.RouteStopTranslation;
import eu.uniche.iguide.route.domain.RouteTranslation;
import eu.uniche.iguide.route.domain.RouteType;
import eu.uniche.iguide.route.domain.TravelMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A Route with its translations and ordered stops. {@code distanceMeters} is the author's override;
 * {@code computedDistanceMeters} is the path length through the stops' coordinates ({@code 03} section 9).
 * {@code hasWorkingCopy} and {@code workingCopyUpdatedAt} tell whether the Route has pending changes
 * ({@code 07} section 14).
 */
public record RouteResponse(UUID id, UUID projectId, String slug, ResourceStatus status, RouteType type,
                            TravelMode travelMode, RouteDifficulty difficulty, Integer estimatedDurationMinutes,
                            Integer distanceMeters, Integer computedDistanceMeters, boolean loop,
                            String internalName, boolean featured, int sortOrder, Instant archivedAt,
                            Instant createdAt, Instant updatedAt, long version, boolean hasWorkingCopy,
                            Instant workingCopyUpdatedAt, Map<String, RouteTranslationBody> translations,
                            List<Stop> stops, List<UUID> categoryIds,
                            List<AssetResponse.Binding> media) {

    /** A stop, at its 0-based position. */
    public record Stop(UUID id, UUID poiId, int position, Map<String, StopTranslationBody> translations) {

        public static Stop from(StopView view) {
            Map<String, StopTranslationBody> texts = view.translations().stream()
                    .collect(Collectors.toMap(RouteStopTranslation::getLanguageCode, StopTranslationBody::from,
                            (a, b) -> a, TreeMap::new));
            return new Stop(view.stop().getId(), view.stop().getPoiId(), view.stop().getPosition(), texts);
        }
    }

    public static RouteResponse from(RouteView view) {
        return from(view, null);
    }

    public static RouteResponse from(RouteView view, Instant workingCopyUpdatedAt) {
        Route r = view.route();
        Map<String, RouteTranslationBody> texts = view.translations().stream()
                .collect(Collectors.toMap(RouteTranslation::getLanguageCode, RouteTranslationBody::from,
                        (a, b) -> a, TreeMap::new));
        return new RouteResponse(r.getId(), r.getProjectId(), r.getSlug(), r.getStatus(), r.getType(),
                r.getTravelMode(), r.getDifficulty(), r.getEstimatedDurationMinutes(), r.getDistanceMeters(),
                view.computedDistanceMeters(), r.isLoop(), r.getInternalName(), r.isFeatured(), r.getSortOrder(),
                r.getArchivedAt(), r.getCreatedAt(), r.getUpdatedAt(), r.getVersion(), workingCopyUpdatedAt != null, workingCopyUpdatedAt, texts,
                view.stops().stream().map(Stop::from).toList(), view.categoryIds(),
                view.media().stream().map(AssetResponse.Binding::from).toList());
    }
}
