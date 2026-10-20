package eu.uniche.iguide.place.api;

import eu.uniche.iguide.place.domain.PlaceEnvironment;
import eu.uniche.iguide.place.domain.PlaceType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * The full editable state of a DRAFT Place. {@code version} is the one last read ({@code 06} section 16).
 * {@code translations} is keyed by language code; a language left out keeps its text.
 */
public record UpdatePlaceRequest(@NotNull Long version, String slug, PlaceType type, PlaceEnvironment environment,
                                 UUID parentPlaceId, String internalName, BigDecimal latitude, BigDecimal longitude,
                                 boolean featured, Map<String, PlaceTranslationBody> translations) {
}
