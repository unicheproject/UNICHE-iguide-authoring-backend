package eu.uniche.iguide.map.domain;

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
 * A Geographic Map ({@code 03} section 7) [ARC_3.3.4]. It has no visitor-facing text of its own: visitor
 * labels come from what is placed on it, so it has no translation table. {@code placeId} links it to an
 * {@code OUTDOOR} Place, which has at most one map.
 */
@Entity
@Table(name = "geographic_maps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GeographicMap {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "place_id")
    private UUID placeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceStatus status;

    @Column(name = "internal_name", nullable = false)
    private String internalName;

    @Column(name = "initial_latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal initialLatitude;

    @Column(name = "initial_longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal initialLongitude;

    @Column(name = "initial_zoom", nullable = false, precision = 5, scale = 2)
    private BigDecimal initialZoom;

    @Column(name = "min_latitude", precision = 9, scale = 6)
    private BigDecimal minLatitude;

    @Column(name = "min_longitude", precision = 9, scale = 6)
    private BigDecimal minLongitude;

    @Column(name = "max_latitude", precision = 9, scale = 6)
    private BigDecimal maxLatitude;

    @Column(name = "max_longitude", precision = 9, scale = 6)
    private BigDecimal maxLongitude;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private MapVisibility visibility;

    @Column(name = "visitor_filter_enabled", nullable = false)
    private boolean visitorFilterEnabled;

    /** Author-side details that never reach visitors, such as the searched area. */
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

    public static GeographicMap draft(UUID projectId, MapView view, Map<String, Object> configuration,
                                      String subject, Instant now) {
        GeographicMap map = new GeographicMap();
        map.projectId = projectId;
        map.status = ResourceStatus.DRAFT;
        map.visibility = MapVisibility.allVisible();
        map.visitorFilterEnabled = true;
        map.configuration = new HashMap<>(configuration);
        map.createdAt = now;
        map.createdBySubject = subject;
        map.apply(view, subject, now);
        return map;
    }

    public void edit(MapView view, MapVisibility visibility, boolean visitorFilterEnabled, String subject, Instant now) {
        this.visibility = visibility;
        this.visitorFilterEnabled = visitorFilterEnabled;
        apply(view, subject, now);
    }

    public void changeStatus(ResourceStatus target, String subject, Instant now) {
        this.status = target;
        this.archivedAt = target == ResourceStatus.ARCHIVED ? now : null;
        if (target == ResourceStatus.ARCHIVED) {
            // An archived map frees its Place, which may then show another map.
            this.placeId = null;
        }
        this.updatedBySubject = subject;
        this.updatedAt = now;
    }

    private void apply(MapView view, String subject, Instant now) {
        this.internalName = view.internalName();
        this.placeId = view.placeId();
        this.initialLatitude = view.initialLatitude();
        this.initialLongitude = view.initialLongitude();
        this.initialZoom = view.initialZoom();
        this.minLatitude = view.minLatitude();
        this.minLongitude = view.minLongitude();
        this.maxLatitude = view.maxLatitude();
        this.maxLongitude = view.maxLongitude();
        this.updatedBySubject = subject;
        this.updatedAt = now;
    }

    /** The map's name, Place link, initial view, and optional bounds. */
    public record MapView(String internalName, UUID placeId, BigDecimal initialLatitude, BigDecimal initialLongitude,
                          BigDecimal initialZoom, BigDecimal minLatitude, BigDecimal minLongitude,
                          BigDecimal maxLatitude, BigDecimal maxLongitude) {
    }
}
