package eu.uniche.iguide.route.api;

import eu.uniche.iguide.route.application.RouteTranslationInput;
import eu.uniche.iguide.route.application.StopTranslationInput;
import eu.uniche.iguide.route.domain.RouteDifficulty;
import eu.uniche.iguide.route.domain.RouteType;
import eu.uniche.iguide.route.domain.TravelMode;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Request bodies of the Route endpoints. */
public final class RouteRequests {

    private RouteRequests() {
    }

    /** {@code title} is in the default language; {@code slug} is generated when absent. */
    public record Create(String title, String slug, RouteType type) {
    }

    /** The Route's own fields; its stops are the {@code .../stops} child collection. */
    public record Update(@NotNull Long version, String slug, RouteType type, TravelMode travelMode,
                         RouteDifficulty difficulty, Integer estimatedDurationMinutes, Integer distanceMeters,
                         boolean loop, String internalName, boolean featured,
                         Map<String, RouteTranslationBody> translations) {

        Map<String, RouteTranslationInput> translationInputs() {
            return translations == null ? Map.of() : translations.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey, e -> e.getValue().toInput()));
        }
    }

    /** A stop: its Point of Interest and its Route-specific texts per language. */
    public record Stop(UUID poiId, Map<String, StopTranslationBody> translations) {

        Map<String, StopTranslationInput> translationInputs() {
            return translations == null ? Map.of() : translations.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey, e -> e.getValue().toInput()));
        }
    }
}
