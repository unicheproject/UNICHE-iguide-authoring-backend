package eu.uniche.iguide.template.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
 * Template provenance of a Project ({@code 04} section 7): which Template version initialized it, when, by whom,
 * and a copy of that version as it was applied. One per Project.
 */
@Entity
@Table(name = "project_template_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectTemplateApplication {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "template_key_snapshot", nullable = false)
    private String templateKeySnapshot;

    @Column(name = "template_version_snapshot", nullable = false)
    private int templateVersionSnapshot;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "applied_by_subject", nullable = false)
    private String appliedBySubject;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "original_template_snapshot", nullable = false)
    private Map<String, Object> originalTemplateSnapshot = new HashMap<>();

    public static ProjectTemplateApplication of(UUID projectId) {
        ProjectTemplateApplication application = new ProjectTemplateApplication();
        application.projectId = projectId;
        return application;
    }

    public void apply(ApplicationTemplate template, Map<String, Object> snapshot, String subject, Instant now) {
        this.templateId = template.getId();
        this.templateKeySnapshot = template.getKey();
        this.templateVersionSnapshot = template.getVersion();
        this.originalTemplateSnapshot = new HashMap<>(snapshot);
        this.appliedBySubject = subject;
        this.appliedAt = now;
    }
}
