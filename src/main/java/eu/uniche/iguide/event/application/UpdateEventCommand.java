package eu.uniche.iguide.event.application;

import java.util.Map;
import java.util.UUID;

/**
 * The Event's own editable fields; its occurrences are the {@code .../occurrences} child collection. A
 * language absent from {@code translations} keeps its text; a blank field clears it.
 */
public record UpdateEventCommand(Long version, String slug, UUID placeId, UUID poiId, UUID routeId, String bookingUrl,
                                 String internalName, boolean featured, Map<String, EventTranslationInput> translations) {
}
