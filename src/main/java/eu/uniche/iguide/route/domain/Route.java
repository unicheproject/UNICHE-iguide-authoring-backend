package eu.uniche.iguide.route.domain;

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
 * A Route ({@code 03} section 9) [ARC_3.3.4]: an ordered journey through Points of Interest. Its stops
 * are {@link RouteStop} rows; its visitor-facing text is in {@link RouteTranslation}.
 * {@code distanceMeters} is the author's override of the distance computed through the stops.
 */
@Entity
@Table(name = "routes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Route {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RouteType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "travel_mode")
    private TravelMode travelMode;

    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(name = "distance_meters")
    private Integer distanceMeters;

    @Enumerated(EnumType.STRING)
    private RouteDifficulty difficulty;

    @Column(nullable = false)
    private boolean loop;

    @Column(name = "internal_name")
    private String internalName;

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

    public static Route draft(UUID projectId, String slug, RouteType type, int sortOrder, String subject, Instant now) {
        Route route = new Route();
        route.projectId = projectId;
        route.slug = slug;
        route.type = type;
        route.status = ResourceStatus.DRAFT;
        route.sortOrder = sortOrder;
        route.createdAt = now;
        route.createdBySubject = subject;
        route.touch(subject, now);
        return route;
    }

    public void edit(String slug, RouteType type, TravelMode travelMode, RouteDifficulty difficulty,
                     Integer estimatedDurationMinutes, Integer distanceMeters, boolean loop, String internalName,
                     boolean featured, String subject, Instant now) {
        this.slug = slug;
        this.type = type;
        this.travelMode = travelMode;
        this.difficulty = difficulty;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.distanceMeters = distanceMeters;
        this.loop = loop;
        this.internalName = internalName;
        this.featured = featured;
        touch(subject, now);
    }

    public void changeStatus(ResourceStatus target, String subject, Instant now) {
        this.status = target;
        this.archivedAt = target == ResourceStatus.ARCHIVED ? now : null;
        touch(subject, now);
    }

    /** A stop change is a change of the Route: it records who and when. */
    public void touch(String subject, Instant now) {
        this.updatedBySubject = subject;
        this.updatedAt = now;
    }
}
