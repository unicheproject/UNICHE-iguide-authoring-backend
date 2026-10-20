package eu.uniche.iguide.language.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A language of a Project ({@code 03} section 3) [ARC_4.3]. Exactly one enabled language is the default,
 * and it is the only fallback. A removed language is disabled rather than deleted, so its translations
 * survive and return when it is enabled again.
 */
@Entity
@Table(name = "project_languages")
@IdClass(ProjectLanguageId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectLanguage {

    @Id
    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Id
    @Column(name = "language_code", nullable = false, updatable = false)
    private String languageCode;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TextDirection direction;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "is_default", nullable = false)
    private boolean defaultLanguage;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static ProjectLanguage of(UUID projectId, String languageCode) {
        ProjectLanguage language = new ProjectLanguage();
        language.projectId = projectId;
        language.languageCode = languageCode;
        return language;
    }

    public void enable(String displayName, TextDirection direction, boolean defaultLanguage, int sortOrder) {
        this.displayName = displayName;
        this.direction = direction;
        this.defaultLanguage = defaultLanguage;
        this.sortOrder = sortOrder;
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
        this.defaultLanguage = false;
    }

    public void clearDefault() {
        this.defaultLanguage = false;
    }
}
