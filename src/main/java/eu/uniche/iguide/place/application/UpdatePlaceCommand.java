package eu.uniche.iguide.place.application;

import eu.uniche.iguide.place.domain.PlaceEnvironment;
import eu.uniche.iguide.place.domain.PlaceType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * The full editable state of a DRAFT Place. A language absent from {@code translations} keeps its text;
 * a blank field clears it.
 */
public record UpdatePlaceCommand(Long version, String slug, PlaceType type, PlaceEnvironment environment,
                                 UUID parentPlaceId, String internalName, BigDecimal latitude, BigDecimal longitude,
                                 boolean featured, Map<String, PlaceTranslationInput> translations) {
}
