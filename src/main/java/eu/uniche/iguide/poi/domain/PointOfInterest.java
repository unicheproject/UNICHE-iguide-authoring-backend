package eu.uniche.iguide.poi.domain;

import eu.uniche.iguide.resource.domain.ResourceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
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
 * A Point of Interest ({@code 03} section 6) [ARC_3.3.4]: a reusable visitor-facing object or spot,
 * optionally inside a Place. {@code code} is the exhibit number a visitor types to open it; it is not an
 * Interaction Trigger code. Its visitor-facing text is in {@link PoiTranslation}.
 */
@Entity
@Table(name = "points_of_interest")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointOfInterest {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "place_id")
    private UUID placeId;

    @Column(nullable = false)
    private String slug;

    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceStatus status;

    @Column(name = "internal_name")
    private String internalName;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "estimated_visit_minutes")
    private Integer estimatedVisitMinutes;

    @Column(nullable = false)
    private boolean featured;

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

    public static PointOfInterest draft(UUID projectId, UUID placeId, String slug, String code, int sortOrder,
                                        String subject, Instant now) {
        PointOfInterest poi = new PointOfInterest();
        poi.projectId = projectId;
        poi.placeId = placeId;
        poi.slug = slug;
        poi.code = code;
        poi.status = ResourceStatus.DRAFT;
        poi.sortOrder = sortOrder;
        poi.createdAt = now;
        poi.createdBySubject = subject;
        poi.touch(subject, now);
        return poi;
    }

    public void edit(UUID placeId, String slug, String code, String internalName, BigDecimal latitude,
                     BigDecimal longitude, Integer estimatedVisitMinutes, boolean featured, String subject,
                     Instant now) {
        this.placeId = placeId;
        this.slug = slug;
        this.code = code;
        this.internalName = internalName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.estimatedVisitMinutes = estimatedVisitMinutes;
        this.featured = featured;
        touch(subject, now);
    }

    public void changeStatus(ResourceStatus target, String subject, Instant now) {
        this.status = target;
        this.archivedAt = target == ResourceStatus.ARCHIVED ? now : null;
        touch(subject, now);
    }

    private void touch(String subject, Instant now) {
        this.updatedBySubject = subject;
        this.updatedAt = now;
    }
}
