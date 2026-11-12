package eu.uniche.iguide.template.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A platform-owned style preset ({@code 04} section 9): a named combination of the two fonts, the four colors,
 * {@code cornerRadius}, and {@code density}, seeded from {@code style-presets/{key}.json} (section 15).
 * {@code checksum} covers {@code values}.
 */
@Entity
@Table(name = "style_presets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StylePreset {

    @Id
    @Column(name = "preset_key", nullable = false, updatable = false)
    private String key;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> name = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> description = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preset_values", nullable = false)
    private Map<String, String> values = new HashMap<>();

    @Column(nullable = false)
    private String checksum;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static StylePreset seed(String key, Instant now) {
        StylePreset preset = new StylePreset();
        preset.key = key;
        preset.createdAt = now;
        return preset;
    }

    public void write(Map<String, String> name, Map<String, String> description, Map<String, String> values,
                      String checksum, Instant now) {
        this.name = new HashMap<>(name);
        this.description = new HashMap<>(description);
        this.values = new HashMap<>(values);
        this.checksum = checksum;
        this.updatedAt = now;
    }

    public void describe(Map<String, String> name, Map<String, String> description, Instant now) {
        this.name = new HashMap<>(name);
        this.description = new HashMap<>(description);
        this.updatedAt = now;
    }
}
