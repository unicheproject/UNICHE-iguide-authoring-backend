package eu.uniche.iguide.guide.domain;

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

/** The title and summary of a Guide in one language ({@code 03} section 11); the body is in its blocks. */
@Entity
@Table(name = "guide_translations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuideTranslation {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "guide_id", nullable = false, updatable = false)
    private UUID guideId;

    @Column(name = "language_code", nullable = false, updatable = false)
    private String languageCode;

    private String title;

    private String summary;

    /** Content provenance ({@code 03} section 1); null means written by a person. Not exposed yet. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> provenance;

    public static GuideTranslation of(UUID projectId, UUID guideId, String languageCode) {
        GuideTranslation translation = new GuideTranslation();
        translation.projectId = projectId;
        translation.guideId = guideId;
        translation.languageCode = languageCode;
        return translation;
    }

    public void write(String title, String summary) {
        this.title = title;
        this.summary = summary;
    }

    public boolean hasTitle() {
        return title != null && !title.isBlank();
    }
}
