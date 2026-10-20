package eu.uniche.iguide.place.domain;

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
 * The visitor-facing text of a Place in one language; {@code (projectId, placeId, languageCode)} is
 * unique ({@code 03} section 5, {@code 06} section 4).
 */
@Entity
@Table(name = "place_translations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceTranslation {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "place_id", nullable = false, updatable = false)
    private UUID placeId;

    @Column(name = "language_code", nullable = false, updatable = false)
    private String languageCode;

    private String title;

    private String summary;

    private String description;

    @Column(name = "visitor_information")
    private String visitorInformation;

    @Column(name = "accessibility_information")
    private String accessibilityInformation;

    /** Content provenance ({@code 03} section 1); null means written by a person. Not exposed yet. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> provenance;

    public static PlaceTranslation of(UUID projectId, UUID placeId, String languageCode) {
        PlaceTranslation translation = new PlaceTranslation();
        translation.projectId = projectId;
        translation.placeId = placeId;
        translation.languageCode = languageCode;
        return translation;
    }

    public void write(String title, String summary, String description, String visitorInformation,
                      String accessibilityInformation) {
        this.title = title;
        this.summary = summary;
        this.description = description;
        this.visitorInformation = visitorInformation;
        this.accessibilityInformation = accessibilityInformation;
    }

    public boolean hasTitle() {
        return title != null && !title.isBlank();
    }
}
