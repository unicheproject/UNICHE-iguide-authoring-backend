package eu.uniche.iguide.event.domain;

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
 * An Event ({@code 03} section 15) [ARC_3.3.4]: a happening visitors can plan around, optionally at a
 * Place, a Point of Interest, or along a Route, taking place at its {@link EventOccurrence}s.
 */
@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

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

    @Column(name = "place_id")
    private UUID placeId;

    @Column(name = "poi_id")
    private UUID poiId;

    @Column(name = "route_id")
    private UUID routeId;

    @Column(name = "booking_url")
    private String bookingUrl;

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

    public static Event draft(UUID projectId, String slug, int sortOrder, String subject, Instant now) {
        Event event = new Event();
        event.projectId = projectId;
        event.slug = slug;
        event.status = ResourceStatus.DRAFT;
        event.sortOrder = sortOrder;
        event.createdAt = now;
        event.createdBySubject = subject;
        event.touch(subject, now);
        return event;
    }

    public void edit(String slug, UUID placeId, UUID poiId, UUID routeId, String bookingUrl, String internalName,
                     boolean featured, String subject, Instant now) {
        this.slug = slug;
        this.placeId = placeId;
        this.poiId = poiId;
        this.routeId = routeId;
        this.bookingUrl = bookingUrl;
        this.internalName = internalName;
        this.featured = featured;
        touch(subject, now);
    }

    public void changeStatus(ResourceStatus target, String subject, Instant now) {
        this.status = target;
        this.archivedAt = target == ResourceStatus.ARCHIVED ? now : null;
        touch(subject, now);
    }

    /** An occurrence change is a change of the Event: it records who and when. */
    public void touch(String subject, Instant now) {
        this.updatedBySubject = subject;
        this.updatedAt = now;
    }
}
