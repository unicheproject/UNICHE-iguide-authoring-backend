package eu.uniche.iguide.floorplan.domain;

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

/** A Resource on a Floor Plan at normalized {@code x} and {@code y} from 0 to 1 ({@code 03} section 8). */
@Entity
@Table(name = "floor_plan_positions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FloorPlanPosition {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "floor_plan_id", nullable = false, updatable = false)
    private UUID floorPlanId;

    @Column(name = "resource_type", nullable = false, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(nullable = false, precision = 6, scale = 5)
    private BigDecimal x;

    @Column(nullable = false, precision = 6, scale = 5)
    private BigDecimal y;

    private Integer rotation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> presentation = new HashMap<>();

    public static FloorPlanPosition of(UUID projectId, UUID floorPlanId, String resourceType, UUID resourceId,
                                       BigDecimal x, BigDecimal y, Integer rotation) {
        FloorPlanPosition position = new FloorPlanPosition();
        position.projectId = projectId;
        position.floorPlanId = floorPlanId;
        position.resourceType = resourceType;
        position.resourceId = resourceId;
        position.moveTo(x, y, rotation);
        return position;
    }

    public void moveTo(BigDecimal x, BigDecimal y, Integer rotation) {
        this.x = x;
        this.y = y;
        this.rotation = rotation;
    }
}
