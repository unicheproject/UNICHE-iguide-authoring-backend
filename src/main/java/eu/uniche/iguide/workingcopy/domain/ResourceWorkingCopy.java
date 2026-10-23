package eu.uniche.iguide.workingcopy.domain;

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
 * The pending edit of one ACTIVE Resource ({@code 03} section 1, {@code 06} section 17). {@code payload} holds
 * the Resource's full editable state, children included; {@code baseVersion} is the live row's version the
 * copy was made from.
 */
@Entity
@Table(name = "resource_working_copies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResourceWorkingCopy {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, updatable = false)
    private WorkingCopyResourceType resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> payload = new HashMap<>();

    @Column(name = "base_version", nullable = false)
    private long baseVersion;

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

    public static ResourceWorkingCopy create(UUID projectId, WorkingCopyResourceType resourceType, UUID resourceId,
                                             Map<String, Object> payload, long baseVersion, String subject,
                                             Instant now) {
        ResourceWorkingCopy copy = new ResourceWorkingCopy();
        copy.projectId = projectId;
        copy.resourceType = resourceType;
        copy.resourceId = resourceId;
        copy.payload = new HashMap<>(payload);
        copy.baseVersion = baseVersion;
        copy.createdAt = now;
        copy.createdBySubject = subject;
        copy.updatedAt = now;
        copy.updatedBySubject = subject;
        return copy;
    }

    public void replace(Map<String, Object> payload, long baseVersion, String subject, Instant now) {
        this.payload = new HashMap<>(payload);
        this.baseVersion = baseVersion;
        this.updatedAt = now;
        this.updatedBySubject = subject;
    }
}
