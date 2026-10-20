package eu.uniche.iguide.poi.application;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * The full editable state of a DRAFT Point of Interest. A language absent from {@code translations} keeps
 * its text; a blank field clears it.
 */
public record UpdatePoiCommand(Long version, String slug, UUID placeId, String code, String internalName,
                               BigDecimal latitude, BigDecimal longitude, Integer estimatedVisitMinutes,
                               boolean featured, Map<String, PoiTranslationInput> translations) {
}
