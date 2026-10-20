package eu.uniche.iguide.place.api;

import eu.uniche.iguide.place.domain.PlaceEnvironment;
import eu.uniche.iguide.place.domain.PlaceType;
import java.util.UUID;

/**
 * @param title       the title in the Project's default language
 * @param slug        optional; generated from the title when absent
 * @param environment optional; {@code INDOOR} for buildings, floors, and rooms, else {@code OUTDOOR}
 */
public record CreatePlaceRequest(String title, String slug, PlaceType type, PlaceEnvironment environment,
                                 UUID parentPlaceId) {
}
