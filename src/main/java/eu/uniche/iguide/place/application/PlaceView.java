package eu.uniche.iguide.place.application;

import eu.uniche.iguide.asset.domain.ResourceAsset;
import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.domain.PlaceTranslation;
import java.util.List;

/** A Place with its translations and its media bindings in order (DECISIONS.md D-041). */
public record PlaceView(Place place, List<PlaceTranslation> translations, List<ResourceAsset> media) {
}
