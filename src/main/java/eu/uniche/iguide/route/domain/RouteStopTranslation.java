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

/**
 * Route-specific text of a stop in one language ({@code 03} section 9). It never changes the Point of
 * Interest's own text ({@code 08} Milestone 5); a null {@code titleOverride} means the POI title is used.
 */
@Entity
@Table(name = "route_stop_translations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RouteStopTranslation {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "route_stop_id", nullable = false, updatable = false)
    private UUID routeStopId;

    @Column(name = "language_code", nullable = false, updatable = false)
    private String languageCode;

    @Column(name = "title_override")
    private String titleOverride;

    private String description;

    @Column(name = "navigation_instructions")
    private String navigationInstructions;

    private String narrative;

    /** Content provenance ({@code 03} section 1); null means written by a person. Not exposed yet. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> provenance;

    public static RouteStopTranslation of(UUID projectId, UUID routeStopId, String languageCode) {
        RouteStopTranslation translation = new RouteStopTranslation();
        translation.projectId = projectId;
        translation.routeStopId = routeStopId;
        translation.languageCode = languageCode;
        return translation;
    }

    public void write(String titleOverride, String description, String navigationInstructions, String narrative) {
        this.titleOverride = titleOverride;
        this.description = description;
        this.navigationInstructions = navigationInstructions;
        this.narrative = narrative;
    }
}
