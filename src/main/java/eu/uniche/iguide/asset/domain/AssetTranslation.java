package eu.uniche.iguide.asset.domain;

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
 * The texts of an Asset in one language ({@code 03} section 4): {@code alternativeText} for images and
 * {@code transcript} for audio and video are its accessibility texts ({@code 14}).
 */
@Entity
@Table(name = "asset_translations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetTranslation {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "language_code", nullable = false, updatable = false)
    private String languageCode;

    private String title;

    private String caption;

    @Column(name = "alternative_text")
    private String alternativeText;

    private String credits;

    private String transcript;

    /** Content provenance ({@code 03} section 1); null means written by a person. Not exposed yet. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> provenance;

    public static AssetTranslation of(UUID projectId, UUID assetId, String languageCode) {
        AssetTranslation translation = new AssetTranslation();
        translation.projectId = projectId;
        translation.assetId = assetId;
        translation.languageCode = languageCode;
        return translation;
    }

    public void write(String title, String caption, String alternativeText, String credits, String transcript) {
        this.title = title;
        this.caption = caption;
        this.alternativeText = alternativeText;
        this.credits = credits;
        this.transcript = transcript;
    }

    public boolean hasAlternativeText() {
        return alternativeText != null && !alternativeText.isBlank();
    }
}
