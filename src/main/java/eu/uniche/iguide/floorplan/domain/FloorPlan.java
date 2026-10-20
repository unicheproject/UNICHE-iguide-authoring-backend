package eu.uniche.iguide.floorplan.domain;

import eu.uniche.iguide.resource.domain.ResourceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A Floor Plan ({@code 03} section 8): an indoor image with Resources at normalized coordinates. An
 * {@code INDOOR} Place may have several, ordered lowest floor first by {@code sortOrder} for the visitor
 * floor switcher. {@code assetId} names an
 * {@code IMAGE} or {@code FLOOR_PLAN} Asset; it is required to activate.
 */
@Entity
@Table(name = "floor_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FloorPlan {

    public static final String NORMALIZED = "NORMALIZED";

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "place_id")
    private UUID placeId;

    @Column(name = "asset_id")
    private UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceStatus status;

    @Column(name = "internal_name", nullable = false)
    private String internalName;

    private Integer width;

    private Integer height;

    @Column(name = "coordinate_system", nullable = false)
    private String coordinateSystem;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> configuration = new HashMap<>();

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by_subject", nullable = false, updatable = false)
    private String createdBySubject;

    @Column(name = "updated_by_subject", nullable = false)
    private String updatedBySubject;

    @Version
    @Column(nullable = false)
    private long version;

    public static FloorPlan draft(UUID projectId, Details details, int sortOrder, String subject, Instant now) {
        FloorPlan plan = new FloorPlan();
        plan.projectId = projectId;
        plan.status = ResourceStatus.DRAFT;
        plan.coordinateSystem = NORMALIZED;
        plan.sortOrder = sortOrder;
        plan.createdAt = now;
        plan.createdBySubject = subject;
        plan.edit(details, subject, now);
        return plan;
    }

    public void edit(Details details, String subject, Instant now) {
        this.internalName = details.internalName();
        this.placeId = details.placeId();
        this.assetId = details.assetId();
        this.width = details.width();
        this.height = details.height();
        this.updatedBySubject = subject;
        this.updatedAt = now;
    }

    public void changeStatus(ResourceStatus target, String subject, Instant now) {
        this.status = target;
        this.archivedAt = target == ResourceStatus.ARCHIVED ? now : null;
        if (target == ResourceStatus.ARCHIVED) {
            // An archived plan no longer represents its Place, which may then change environment.
            this.placeId = null;
        }
        this.updatedBySubject = subject;
        this.updatedAt = now;
    }

    /** The editable fields of a Floor Plan; {@code width} and {@code height} are the image's pixels. */
    public record Details(String internalName, UUID placeId, UUID assetId, Integer width, Integer height) {
    }
}
