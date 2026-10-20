package eu.uniche.iguide.event.api;

import eu.uniche.iguide.event.application.EventTranslationInput;
import eu.uniche.iguide.event.domain.OccurrenceStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Request bodies of the Event endpoints. */
public final class EventRequests {

    private EventRequests() {
    }

    /** {@code title} is in the default language; {@code slug} is generated when absent. */
    public record Create(String title, String slug) {
    }

    /** The Event's own fields; its occurrences are the {@code .../occurrences} child collection. */
    public record Update(@NotNull Long version, String slug, UUID placeId, UUID poiId, UUID routeId, String bookingUrl,
                         String internalName, boolean featured, Map<String, EventTranslationBody> translations) {

        Map<String, EventTranslationInput> translationInputs() {
            return translations == null ? Map.of() : translations.entrySet().stream().collect(Collectors.toMap(
                    Map.Entry::getKey, e -> e.getValue().toInput()));
        }
    }

    /** One occurrence; instants in ISO-8601. A missing status means {@code SCHEDULED}. */
    public record Occurrence(Instant startsAt, Instant endsAt, OccurrenceStatus status) {
    }
}
