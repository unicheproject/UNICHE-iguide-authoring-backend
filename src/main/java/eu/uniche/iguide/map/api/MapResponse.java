package eu.uniche.iguide.map.api;

import eu.uniche.iguide.map.domain.GeographicMap;
import eu.uniche.iguide.map.domain.GeographicMapResource;
import eu.uniche.iguide.map.domain.MapVisibility;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A Geographic Map. Its placements are a child collection, {@code .../maps/{id}/resources}. {@code hasWorkingCopy}
 * and {@code workingCopyUpdatedAt} tell whether the map has pending changes ({@code 07} section 14).
 */
public record MapResponse(UUID id, UUID projectId, UUID placeId, ResourceStatus status, String internalName,
                          BigDecimal initialLatitude, BigDecimal initialLongitude, BigDecimal initialZoom,
                          BigDecimal minLatitude, BigDecimal minLongitude, BigDecimal maxLatitude,
                          BigDecimal maxLongitude, MapVisibility visibility, boolean visitorFilterEnabled,
                          String area, String osmId, Instant archivedAt, Instant createdAt, Instant updatedAt,
                          long version, boolean hasWorkingCopy, Instant workingCopyUpdatedAt) {

    public static MapResponse from(GeographicMap m) {
        return from(m, null);
    }

    public static MapResponse from(GeographicMap m, Instant workingCopyUpdatedAt) {
        return new MapResponse(m.getId(), m.getProjectId(), m.getPlaceId(), m.getStatus(), m.getInternalName(),
                m.getInitialLatitude(), m.getInitialLongitude(), m.getInitialZoom(), m.getMinLatitude(),
                m.getMinLongitude(), m.getMaxLatitude(), m.getMaxLongitude(), m.getVisibility(),
                m.isVisitorFilterEnabled(), (String) m.getConfiguration().get("area"),
                (String) m.getConfiguration().get("osmId"), m.getArchivedAt(), m.getCreatedAt(), m.getUpdatedAt(),
                m.getVersion(), workingCopyUpdatedAt != null, workingCopyUpdatedAt);
    }

    public record Placement(UUID id, String resourceType, UUID resourceId, BigDecimal latitude, BigDecimal longitude) {

        public static Placement from(GeographicMapResource r) {
            return new Placement(r.getId(), r.getResourceType(), r.getResourceId(), r.getLatitude(), r.getLongitude());
        }
    }
}
