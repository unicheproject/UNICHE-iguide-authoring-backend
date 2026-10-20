package eu.uniche.iguide.poi.domain;

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
 * The visitor-facing text of a Point of Interest in one language; {@code (projectId, poiId,
 * languageCode)} is unique ({@code 03} section 6, {@code 06} section 4).
 */
@Entity
@Table(name = "poi_translations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PoiTranslation {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "poi_id", nullable = false, updatable = false)
    private UUID poiId;

    @Column(name = "language_code", nullable = false, updatable = false)
    private String languageCode;

    private String title;

    private String summary;

    private String description;

    private String instructions;

    @Column(name = "accessibility_information")
    private String accessibilityInformation;

    /** Content provenance ({@code 03} section 1); null means written by a person. Not exposed yet. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> provenance;

    public static PoiTranslation of(UUID projectId, UUID poiId, String languageCode) {
        PoiTranslation translation = new PoiTranslation();
        translation.projectId = projectId;
        translation.poiId = poiId;
        translation.languageCode = languageCode;
        return translation;
    }

    public void write(String title, String summary, String description, String instructions,
                      String accessibilityInformation) {
        this.title = title;
        this.summary = summary;
        this.description = description;
        this.instructions = instructions;
        this.accessibilityInformation = accessibilityInformation;
    }

    public boolean hasTitle() {
        return title != null && !title.isBlank();
    }
}
