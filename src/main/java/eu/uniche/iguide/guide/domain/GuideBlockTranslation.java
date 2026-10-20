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

/** The text of a TEXT block, or the caption of a REFERENCE block, in one language ({@code 03} section 11). */
@Entity
@Table(name = "guide_block_translations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuideBlockTranslation {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "guide_block_id", nullable = false, updatable = false)
    private UUID guideBlockId;

    @Column(name = "language_code", nullable = false, updatable = false)
    private String languageCode;

    @JdbcTypeCode(SqlTypes.JSON)
    private RichText text;

    private String caption;

    /** Content provenance ({@code 03} section 1); null means written by a person. Not exposed yet. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> provenance;

    public static GuideBlockTranslation of(UUID projectId, UUID guideBlockId, String languageCode) {
        GuideBlockTranslation translation = new GuideBlockTranslation();
        translation.projectId = projectId;
        translation.guideBlockId = guideBlockId;
        translation.languageCode = languageCode;
        return translation;
    }

    public void write(RichText text, String caption) {
        this.text = text;
        this.caption = caption;
    }

    public boolean hasText() {
        return text != null && text.hasText();
    }
}
