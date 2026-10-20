package eu.uniche.iguide.place.application;

import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.domain.PlaceTranslation;
import java.util.List;

public record PlaceView(Place place, List<PlaceTranslation> translations) {
}
