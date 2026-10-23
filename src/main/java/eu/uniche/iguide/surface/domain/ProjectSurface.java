package eu.uniche.iguide.surface.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
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
 * One visitor surface of a Project ({@code 03} section 2): whether it is enabled, and its runtime options, such
 * as the WEB surface's {@code publicBaseUrl} ({@code 04} section 6).
 */
@Entity
@Table(name = "project_surfaces")
@IdClass(ProjectSurfaceId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectSurface {

    public static final String PUBLIC_BASE_URL = "publicBaseUrl";

    @Id
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Surface surface;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> configuration = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static ProjectSurface of(UUID projectId, Surface surface, Instant now) {
        ProjectSurface row = new ProjectSurface();
        row.projectId = projectId;
        row.surface = surface;
        row.sortOrder = surface.ordinal();
        row.createdAt = now;
        row.updatedAt = now;
        return row;
    }

    public void write(boolean enabled, Map<String, Object> configuration, Instant now) {
        this.enabled = enabled;
        this.configuration = new HashMap<>(configuration);
        this.updatedAt = now;
    }

    /** The WEB surface's address, or {@code null}. */
    public String publicBaseUrl() {
        return configuration.get(PUBLIC_BASE_URL) instanceof String url ? url : null;
    }
}
