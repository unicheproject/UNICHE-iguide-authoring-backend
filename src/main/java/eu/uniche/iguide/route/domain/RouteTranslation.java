package eu.uniche.iguide.route.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** The visitor-facing text of a Route in one language ({@code 03} section 9). */
@Entity
@Table(name = "route_translations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteTranslation {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "route_id", nullable = false, updatable = false)
    private UUID routeId;

    @Column(name = "language_code", nullable = false, updatable = false)
    private String languageCode;

    private String title;

    private String summary;

    private String description;

    private String instructions;

    /** Content provenance ({@code 03} section 1); null means written by a person. Not exposed yet. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> provenance;

    public static RouteTranslation of(UUID projectId, UUID routeId, String languageCode) {
        RouteTranslation translation = new RouteTranslation();
        translation.projectId = projectId;
        translation.routeId = routeId;
        translation.languageCode = languageCode;
        return translation;
    }

    public void write(String title, String summary, String description, String instructions) {
        this.title = title;
        this.summary = summary;
        this.description = description;
        this.instructions = instructions;
    }

    public boolean hasTitle() {
        return title != null && !title.isBlank();
    }
}
