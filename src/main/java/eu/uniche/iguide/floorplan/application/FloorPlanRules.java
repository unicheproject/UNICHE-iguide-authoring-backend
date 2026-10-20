package eu.uniche.iguide.floorplan.application;

import eu.uniche.iguide.asset.domain.Asset;
import eu.uniche.iguide.asset.domain.AssetType;
import eu.uniche.iguide.asset.infrastructure.AssetRepository;
import eu.uniche.iguide.floorplan.domain.FloorPlan;
import eu.uniche.iguide.floorplan.domain.FloorPlan.Details;
import eu.uniche.iguide.floorplan.infrastructure.FloorPlanRepository;
import eu.uniche.iguide.place.domain.PlaceEnvironment;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.spatial.application.SpatialRules;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Rules the floor plan use cases share. Callers have already passed the Project access check. */
@Component
public class FloorPlanRules {

    private final FloorPlanRepository plans;
    private final SpatialRules spatial;
    private final AssetRepository assets;

    public FloorPlanRules(FloorPlanRepository plans, SpatialRules spatial, AssetRepository assets) {
        this.plans = plans;
        this.spatial = spatial;
        this.assets = assets;
    }

    public FloorPlan requirePlan(UUID projectId, UUID planId) {
        return SpatialRules.require(plans.findByProjectIdAndId(projectId, planId), "floor plan");
    }

    /**
     * Checks the name, the image, the image size, and the Place link (an {@code INDOOR} Place). The image is
     * an {@code IMAGE} or {@code FLOOR_PLAN} Asset of the Project that is not archived; its pixel size fills
     * {@code width} and {@code height} when they are not given. Returns the details with a trimmed name.
     */
    public Details checkDetails(UUID projectId, Details details, List<ErrorItem> errors) {
        String name = ResourceRules.requireName(details.internalName(), "internalName", errors);
        Integer width = details.width();
        Integer height = details.height();
        if (details.assetId() != null) {
            Optional<Asset> image = assets.findByProjectIdAndId(projectId, details.assetId());
            if (image.isEmpty()) {
                errors.add(new ErrorItem("assetId", "RESOURCE_NOT_FOUND", "No such asset in this project."));
            } else if (image.get().getStatus() == ResourceStatus.ARCHIVED) {
                errors.add(new ErrorItem("assetId", "ARCHIVED_REFERENCE", "The image is archived."));
            } else if (image.get().getType() != AssetType.IMAGE && image.get().getType() != AssetType.FLOOR_PLAN) {
                errors.add(new ErrorItem("assetId", "WRONG_ASSET_TYPE", "The plan image must be an image."));
            } else if (width == null && height == null) {
                width = image.get().getWidth();
                height = image.get().getHeight();
            }
        }
        if (width != null && width <= 0) {
            errors.add(new ErrorItem("width", "OUT_OF_RANGE", "Must be positive."));
        }
        if (height != null && height <= 0) {
            errors.add(new ErrorItem("height", "OUT_OF_RANGE", "Must be positive."));
        }
        spatial.checkLinkedPlace(projectId, details.placeId(), PlaceEnvironment.INDOOR, errors);
        return new Details(name, details.placeId(), details.assetId(), width, height);
    }

    /** Normalized coordinates from 0 to 1 and an optional rotation in whole degrees ({@code 08} Milestone 5). */
    public static void checkPosition(BigDecimal x, BigDecimal y, Integer rotation, List<ErrorItem> errors) {
        checkUnit(x, "x", errors);
        checkUnit(y, "y", errors);
        if (rotation != null && (rotation < 0 || rotation > 359)) {
            errors.add(new ErrorItem("rotation", "OUT_OF_RANGE", "Must be between 0 and 359."));
        }
    }

    private static void checkUnit(BigDecimal value, String path, List<ErrorItem> errors) {
        if (value == null) {
            errors.add(new ErrorItem(path, "REQUIRED", "Is required."));
        } else if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            errors.add(new ErrorItem(path, "OUT_OF_RANGE", "Must be between 0 and 1."));
        }
    }
}
