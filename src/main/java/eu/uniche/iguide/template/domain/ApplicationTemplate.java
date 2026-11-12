package eu.uniche.iguide.template.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One immutable version of a platform-owned Application Template ({@code 04} sections 3 to 6), seeded from its
 * repository file at startup (section 15). Not owned by a Project; never written through the API.
 *
 * <p>{@code checksum} covers the behavioral {@code definition}; {@code status}, {@code name}, and
 * {@code description} may change in the file at a later release (DECISIONS.md D-040).
 */
@Entity
@Table(name = "application_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationTemplate {

    public static final String SEEDER_SUBJECT = "system:template-seeder";

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "template_key", nullable = false, updatable = false)
    private String key;

    @Column(nullable = false, updatable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TemplateStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> name = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> description = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supported_surfaces", nullable = false)
    private List<String> supportedSurfaces = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> definition = new HashMap<>();

    @Column(nullable = false)
    private String checksum;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by_subject", nullable = false, updatable = false)
    private String createdBySubject;

    public static ApplicationTemplate seed(UUID id, String key, int version, Instant now) {
        ApplicationTemplate template = new ApplicationTemplate();
        template.id = id;
        template.key = key;
        template.version = version;
        template.createdAt = now;
        template.createdBySubject = SEEDER_SUBJECT;
        return template;
    }

    public void write(TemplateStatus status, Map<String, String> name, Map<String, String> description,
                      List<String> supportedSurfaces, Map<String, Object> definition, String checksum, Instant now) {
        this.status = status;
        this.name = new HashMap<>(name);
        this.description = new HashMap<>(description);
        this.supportedSurfaces = new ArrayList<>(supportedSurfaces);
        this.definition = new HashMap<>(definition);
        this.checksum = checksum;
        this.updatedAt = now;
    }

    /** Status, name, and description only: the definition of a version stays as seeded. */
    public void describe(TemplateStatus status, Map<String, String> name, Map<String, String> description,
                         Instant now) {
        this.status = status;
        this.name = new HashMap<>(name);
        this.description = new HashMap<>(description);
        this.updatedAt = now;
    }
}
