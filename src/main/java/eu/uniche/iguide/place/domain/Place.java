package eu.uniche.iguide.place.domain;

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
 * A Place ({@code 03} section 5): a region, site, building, floor, room, or area, nested through
 * {@code parentPlaceId}. Its visitor-facing text is in {@link PlaceTranslation}.
 */
@Entity
@Table(name = "places")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "parent_place_id")
    private UUID parentPlaceId;

    @Column(nullable = false)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlaceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlaceEnvironment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceStatus status;

    @Column(name = "internal_name")
    private String internalName;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

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

    public static Place draft(UUID projectId, UUID parentPlaceId, String slug, PlaceType type,
                              PlaceEnvironment environment, int sortOrder, String subject, Instant now) {
        Place place = new Place();
        place.projectId = projectId;
        place.parentPlaceId = parentPlaceId;
        place.slug = slug;
        place.type = type;
        place.environment = environment;
        place.status = ResourceStatus.DRAFT;
        place.sortOrder = sortOrder;
        place.createdAt = now;
        place.createdBySubject = subject;
        place.touch(subject, now);
        return place;
    }

    public void edit(UUID parentPlaceId, String slug, PlaceType type, PlaceEnvironment environment,
                     String internalName, BigDecimal latitude, BigDecimal longitude, boolean featured,
                     String subject, Instant now) {
        this.parentPlaceId = parentPlaceId;
        this.slug = slug;
        this.type = type;
        this.environment = environment;
        this.internalName = internalName;
        this.latitude = latitude;
        this.longitude = longitude;
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
