package eu.uniche.iguide.poi.application;

import eu.uniche.iguide.asset.domain.ResourceAsset;
import eu.uniche.iguide.poi.domain.PoiTranslation;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import java.util.List;
import java.util.UUID;

/**
 * A Point of Interest with its translations, the ids of the Categories it is in (at most one), and its media
 * bindings in order.
 */
public record PoiView(PointOfInterest poi, List<PoiTranslation> translations, List<UUID> categoryIds,
                      List<ResourceAsset> media) {
}
