package eu.uniche.iguide.place.application;

import eu.uniche.iguide.place.domain.PlaceEnvironment;
import eu.uniche.iguide.place.domain.PlaceType;
import java.util.UUID;

/**
 * @param title       in the default language; required
 * @param slug        optional; generated from the title when absent ({@code 03} section 5)
 * @param environment optional; derived from the type when absent
 */
public record CreatePlaceCommand(String title, String slug, PlaceType type, PlaceEnvironment environment,
                                 UUID parentPlaceId) {
}
