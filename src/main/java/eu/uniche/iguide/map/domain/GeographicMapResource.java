package eu.uniche.iguide.map.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A Resource placed on a Geographic Map ({@code 03} section 7), at its own coordinates on that map
 * (DECISIONS.md D-019).
 */
@Entity
@Table(name = "geographic_map_resources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GeographicMapResource {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "map_id", nullable = false, updatable = false)
    private UUID mapId;

    @Column(name = "resource_type", nullable = false, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> presentation = new HashMap<>();

    public static GeographicMapResource of(UUID projectId, UUID mapId, String resourceType, UUID resourceId,
                                           BigDecimal latitude, BigDecimal longitude) {
        GeographicMapResource placement = new GeographicMapResource();
        placement.projectId = projectId;
        placement.mapId = mapId;
        placement.resourceType = resourceType;
        placement.resourceId = resourceId;
        placement.moveTo(latitude, longitude);
        return placement;
    }

    public void moveTo(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
