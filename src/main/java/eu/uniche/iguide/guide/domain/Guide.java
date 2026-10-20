package eu.uniche.iguide.guide.domain;

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
 * A Guide ({@code 03} section 11): an article of ordered {@link GuideBlock}s, text and references
 * interleaved as written. Its title and summary are in {@link GuideTranslation}.
 */
@Entity
@Table(name = "guides")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Guide {

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

    @Column(name = "internal_name")
    private String internalName;

    @Column(name = "estimated_reading_minutes")
    private Integer estimatedReadingMinutes;

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

    public static Guide draft(UUID projectId, String slug, int sortOrder, String subject, Instant now) {
        Guide guide = new Guide();
        guide.projectId = projectId;
        guide.slug = slug;
        guide.status = ResourceStatus.DRAFT;
        guide.sortOrder = sortOrder;
        guide.createdAt = now;
        guide.createdBySubject = subject;
        guide.touch(subject, now);
        return guide;
    }

    public void edit(String slug, String internalName, Integer estimatedReadingMinutes, boolean featured,
                     String subject, Instant now) {
        this.slug = slug;
        this.internalName = internalName;
        this.estimatedReadingMinutes = estimatedReadingMinutes;
        this.featured = featured;
        touch(subject, now);
    }

    public void changeStatus(ResourceStatus target, String subject, Instant now) {
        this.status = target;
        this.archivedAt = target == ResourceStatus.ARCHIVED ? now : null;
        touch(subject, now);
    }

    /** A block change is a change of the Guide: it records who and when. */
    public void touch(String subject, Instant now) {
        this.updatedBySubject = subject;
        this.updatedAt = now;
    }
}
