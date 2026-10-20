package eu.uniche.iguide.route.application;

import eu.uniche.iguide.asset.domain.ResourceAsset;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.domain.RouteTranslation;
import java.util.List;
import java.util.UUID;

/**
 * A Route with its translations and ordered stops. {@code computedDistanceMeters} is the path length
 * through the stops' coordinates, or null when fewer than two stops have coordinates; {@code categoryIds}
 * holds the Route's Category, at most one.
 */
public record RouteView(Route route, List<RouteTranslation> translations, List<StopView> stops,
                        Integer computedDistanceMeters, List<UUID> categoryIds,
                        List<ResourceAsset> media) {
}
