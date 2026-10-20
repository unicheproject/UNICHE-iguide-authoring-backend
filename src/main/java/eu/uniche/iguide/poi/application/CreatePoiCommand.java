package eu.uniche.iguide.poi.application;

import java.util.UUID;

/**
 * @param title in the default language; required
 * @param slug  optional; generated from the title when absent ({@code 03} section 5)
 * @param code  optional exhibit number
 */
public record CreatePoiCommand(String title, String slug, UUID placeId, String code) {
}
