package eu.uniche.iguide.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Project-wide settings ({@code 03} section 2), created by initialization ({@code 04} section 8 step 4). The default
 * language stays in {@code project_languages.is_default} (DECISIONS.md D-019, D-040).
 */
@Entity
@Table(name = "project_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectSettings {

    @Id
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(name = "application_status", nullable = false)
    private ApplicationStatus applicationStatus;

    /** Raised only by {@code ContentRevision}, in SQL: a settings save never writes it back. */
    @Column(name = "content_revision", nullable = false, updatable = false)
    private long contentRevision;

    /** Capability name to enabled, restricted to the applied Template's capabilities. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feature_configuration", nullable = false)
    private Map<String, Boolean> featureConfiguration = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "runtime_configuration", nullable = false)
    private Map<String, Object> runtimeConfiguration = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static ProjectSettings initial(UUID projectId, Instant now) {
        ProjectSettings settings = new ProjectSettings();
        settings.projectId = projectId;
        settings.applicationStatus = ApplicationStatus.DRAFT;
        settings.createdAt = now;
        settings.updatedAt = now;
        return settings;
    }

    public void configureFeatures(Map<String, Boolean> featureConfiguration, Instant now) {
        this.featureConfiguration = new HashMap<>(featureConfiguration);
        this.updatedAt = now;
    }

    public void changeTimezone(String timezone, Instant now) {
        this.timezone = timezone;
        this.updatedAt = now;
    }
}
