package eu.uniche.iguide.floorplan.api;

import eu.uniche.iguide.floorplan.domain.FloorPlan;
import eu.uniche.iguide.floorplan.domain.FloorPlanPosition;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A Floor Plan. Its positions are a child collection, {@code .../floor-plans/{id}/positions}.
 * {@code hasWorkingCopy} and {@code workingCopyUpdatedAt} tell whether the plan has pending changes
 * ({@code 07} section 14).
 */
public record FloorPlanResponse(UUID id, UUID projectId, UUID placeId, UUID assetId, ResourceStatus status,
                                String internalName, Integer width, Integer height, String coordinateSystem,
                                int sortOrder, Instant archivedAt, Instant createdAt, Instant updatedAt, long version,
                                boolean hasWorkingCopy, Instant workingCopyUpdatedAt) {

    public static FloorPlanResponse from(FloorPlan f) {
        return from(f, null);
    }

    public static FloorPlanResponse from(FloorPlan f, Instant workingCopyUpdatedAt) {
        return new FloorPlanResponse(f.getId(), f.getProjectId(), f.getPlaceId(), f.getAssetId(), f.getStatus(),
                f.getInternalName(), f.getWidth(), f.getHeight(), f.getCoordinateSystem(), f.getSortOrder(),
                f.getArchivedAt(), f.getCreatedAt(), f.getUpdatedAt(), f.getVersion(), workingCopyUpdatedAt != null,
                workingCopyUpdatedAt);
    }

    public record Position(UUID id, String resourceType, UUID resourceId, BigDecimal x, BigDecimal y,
                           Integer rotation) {

        public static Position from(FloorPlanPosition p) {
            return new Position(p.getId(), p.getResourceType(), p.getResourceId(), p.getX(), p.getY(), p.getRotation());
        }
    }
}
