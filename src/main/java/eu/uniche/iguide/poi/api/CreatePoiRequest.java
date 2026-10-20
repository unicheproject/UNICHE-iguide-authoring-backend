package eu.uniche.iguide.poi.api;

import java.util.UUID;

/**
 * @param title the title in the Project's default language
 * @param slug  optional; generated from the title when absent
 * @param code  optional exhibit number visitors type to open the POI
 */
public record CreatePoiRequest(String title, String slug, UUID placeId, String code) {
}
