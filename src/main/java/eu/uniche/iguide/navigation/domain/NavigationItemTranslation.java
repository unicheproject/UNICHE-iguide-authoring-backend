package eu.uniche.iguide.navigation.domain;

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

/** The label of a Navigation item in one language; without one the visitor app shows the target's title. */
@Entity
@Table(name = "navigation_item_translations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NavigationItemTranslation {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "navigation_item_id", nullable = false, updatable = false)
    private UUID navigationItemId;

    @Column(name = "language_code", nullable = false, updatable = false)
    private String languageCode;

    private String label;

    /** Content provenance ({@code 03} section 1); null means written by a person. Not exposed yet. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> provenance;

    public static NavigationItemTranslation of(UUID projectId, UUID itemId, String languageCode) {
        NavigationItemTranslation translation = new NavigationItemTranslation();
        translation.projectId = projectId;
        translation.navigationItemId = itemId;
        translation.languageCode = languageCode;
        return translation;
    }

    public void write(String label) {
        this.label = label;
    }
}
