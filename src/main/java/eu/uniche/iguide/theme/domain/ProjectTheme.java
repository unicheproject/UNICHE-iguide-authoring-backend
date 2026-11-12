package eu.uniche.iguide.theme.domain;

import eu.uniche.iguide.template.application.StyleValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The theme of a Project ({@code 04} section 9): every property a typed column, only the application shell JSONB.
 * A {@code null} typed value takes the applied Template's {@code defaultTheme} value (section 10). Created from a
 * style preset at initialization; no live link to the preset or the Template remains.
 */
@Entity
@Table(name = "project_themes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectTheme {

    @Id
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "logo_asset_id")
    private UUID logoAssetId;

    @Column(name = "compact_logo_asset_id")
    private UUID compactLogoAssetId;

    @Column(name = "favicon_asset_id")
    private UUID faviconAssetId;

    @Column(name = "splash_asset_id")
    private UUID splashAssetId;

    @Column(name = "primary_color")
    private String primaryColor;

    @Column(name = "secondary_color")
    private String secondaryColor;

    @Column(name = "background_color")
    private String backgroundColor;

    @Column(name = "text_color")
    private String textColor;

    @Column(name = "style_preset_key")
    private String stylePresetKey;

    @Column(name = "heading_font")
    private String headingFont;

    @Column(name = "body_font")
    private String bodyFont;

    @Column(name = "corner_radius")
    private String cornerRadius;

    private String density;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> shell = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static ProjectTheme of(UUID projectId, Instant now) {
        ProjectTheme theme = new ProjectTheme();
        theme.projectId = projectId;
        theme.createdAt = now;
        theme.updatedAt = now;
        return theme;
    }

    public StyleValues values() {
        return new StyleValues(headingFont, bodyFont, primaryColor, secondaryColor, backgroundColor, textColor,
                cornerRadius, density);
    }

    /** Sets the typed values and the preset; logos and shell are untouched ({@code 04} section 9). */
    public void style(StyleValues values, String stylePresetKey, Instant now) {
        this.headingFont = values.headingFont();
        this.bodyFont = values.bodyFont();
        this.primaryColor = values.primaryColor();
        this.secondaryColor = values.secondaryColor();
        this.backgroundColor = values.backgroundColor();
        this.textColor = values.textColor();
        this.cornerRadius = values.cornerRadius();
        this.density = values.density();
        this.stylePresetKey = stylePresetKey;
        this.updatedAt = now;
    }

    public void brand(UUID logoAssetId, UUID compactLogoAssetId, UUID faviconAssetId, UUID splashAssetId,
                      Instant now) {
        this.logoAssetId = logoAssetId;
        this.compactLogoAssetId = compactLogoAssetId;
        this.faviconAssetId = faviconAssetId;
        this.splashAssetId = splashAssetId;
        this.updatedAt = now;
    }

    public void shell(Map<String, Object> shell, Instant now) {
        this.shell = new LinkedHashMap<>(shell);
        this.updatedAt = now;
    }
}
