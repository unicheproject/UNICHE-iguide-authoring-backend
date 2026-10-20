package eu.uniche.iguide.poi.api;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * The full editable state of a DRAFT Point of Interest. {@code version} is the one last read ({@code 06}
 * section 16). {@code translations} is keyed by language code; a language left out keeps its text.
 */
public record UpdatePoiRequest(@NotNull Long version, String slug, UUID placeId, String code, String internalName,
                               BigDecimal latitude, BigDecimal longitude, Integer estimatedVisitMinutes,
                               boolean featured, Map<String, PoiTranslationBody> translations) {
}
