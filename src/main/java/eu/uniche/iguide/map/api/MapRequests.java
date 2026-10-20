package eu.uniche.iguide.map.api;

import eu.uniche.iguide.map.domain.GeographicMap.MapView;
import eu.uniche.iguide.map.domain.MapVisibility;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** Request bodies of the map endpoints. */
public final class MapRequests {

    private MapRequests() {
    }

    /** {@code area} and {@code osmId} record what the author searched for; they never reach visitors. */
    public record Create(String internalName, UUID placeId, BigDecimal initialLatitude, BigDecimal initialLongitude,
                         BigDecimal initialZoom, BigDecimal minLatitude, BigDecimal minLongitude,
                         BigDecimal maxLatitude, BigDecimal maxLongitude, String area, String osmId) {

        MapView view() {
            return new MapView(internalName, placeId, initialLatitude, initialLongitude, initialZoom, minLatitude,
                    minLongitude, maxLatitude, maxLongitude);
        }
    }

    public record Update(@NotNull Long version, String internalName, UUID placeId, BigDecimal initialLatitude,
                         BigDecimal initialLongitude, BigDecimal initialZoom, BigDecimal minLatitude,
                         BigDecimal minLongitude, BigDecimal maxLatitude, BigDecimal maxLongitude,
                         MapVisibility visibility, boolean visitorFilterEnabled) {

        MapView view() {
            return new MapView(internalName, placeId, initialLatitude, initialLongitude, initialZoom, minLatitude,
                    minLongitude, maxLatitude, maxLongitude);
        }
    }

    public record Place(String resourceType, UUID resourceId, BigDecimal latitude, BigDecimal longitude) {
    }

    public record Move(BigDecimal latitude, BigDecimal longitude) {
    }
}
