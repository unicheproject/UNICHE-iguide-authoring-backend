package eu.uniche.iguide.floorplan.api;

import eu.uniche.iguide.floorplan.domain.FloorPlan.Details;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/** Request bodies of the floor plan endpoints. */
public final class FloorPlanRequests {

    private FloorPlanRequests() {
    }

    /** {@code assetId} is the plan image, an image Asset of the Project; optional until activation. */
    public record Create(String internalName, UUID placeId, UUID assetId, Integer width, Integer height) {

        Details details() {
            return new Details(internalName, placeId, assetId, width, height);
        }
    }

    public record Update(@NotNull Long version, String internalName, UUID placeId, UUID assetId, Integer width,
                         Integer height) {

        Details details() {
            return new Details(internalName, placeId, assetId, width, height);
        }
    }

    public record Position(String resourceType, UUID resourceId, BigDecimal x, BigDecimal y, Integer rotation) {
    }

    public record Move(BigDecimal x, BigDecimal y, Integer rotation) {
    }
}
