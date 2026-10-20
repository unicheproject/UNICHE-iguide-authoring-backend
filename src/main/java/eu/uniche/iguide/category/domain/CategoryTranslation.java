package eu.uniche.iguide.category.domain;

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

/** The name and description of a Category in one language ({@code 03} section 10). */
@Entity
@Table(name = "category_translations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryTranslation {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    @Column(name = "language_code", nullable = false, updatable = false)
    private String languageCode;

    private String name;

    private String description;

    /** Content provenance ({@code 03} section 1); null means written by a person. Not exposed yet. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> provenance;

    public static CategoryTranslation of(UUID projectId, UUID categoryId, String languageCode) {
        CategoryTranslation translation = new CategoryTranslation();
        translation.projectId = projectId;
        translation.categoryId = categoryId;
        translation.languageCode = languageCode;
        return translation;
    }

    public void write(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public boolean hasName() {
        return name != null && !name.isBlank();
    }
}
