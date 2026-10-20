package eu.uniche.iguide.route.application;

import eu.uniche.iguide.route.domain.RouteDifficulty;
import eu.uniche.iguide.route.domain.RouteType;
import eu.uniche.iguide.route.domain.TravelMode;
import java.util.Map;

/**
 * The full editable state of a DRAFT Route, without its stops, which are a child collection. A language
 * absent from {@code translations} keeps its text; a blank field clears it.
 */
public record UpdateRouteCommand(Long version, String slug, RouteType type, TravelMode travelMode,
                                 RouteDifficulty difficulty, Integer estimatedDurationMinutes,
                                 Integer distanceMeters, boolean loop, String internalName, boolean featured,
                                 Map<String, RouteTranslationInput> translations) {
}
