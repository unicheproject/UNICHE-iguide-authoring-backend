package eu.uniche.iguide.category.domain;

import eu.uniche.iguide.resource.domain.ResourceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A Category ({@code 03} section 10): classifies Points of Interest or Routes, at most two levels deep
 * (a top-level Category and its subcategories, which share its scope), shown with a built-in icon.
 */
@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "parent_category_id")
    private UUID parentCategoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategoryScope scope;

    @Column(nullable = false)
    private String code;

    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceStatus status;

    @Column(name = "internal_name")
    private String internalName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> presentation = new HashMap<>();

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by_subject", nullable = false, updatable = false)
    private String createdBySubject;

    @Column(name = "updated_by_subject", nullable = false)
    private String updatedBySubject;

    @Version
    @Column(nullable = false)
    private long version;

    public static Category draft(UUID projectId, UUID parentCategoryId, CategoryScope scope, String code, int sortOrder,
                                 String subject, Instant now) {
        Category category = new Category();
        category.projectId = projectId;
        category.parentCategoryId = parentCategoryId;
        category.scope = scope;
        category.code = code;
        category.status = ResourceStatus.DRAFT;
        category.sortOrder = sortOrder;
        category.createdAt = now;
        category.createdBySubject = subject;
        category.touch(subject, now);
        return category;
    }

    public void edit(String code, CategoryScope scope, UUID parentCategoryId, String icon, String subject, Instant now) {
        this.code = code;
        this.scope = scope;
        this.parentCategoryId = parentCategoryId;
        this.icon = icon;
        touch(subject, now);
    }

    /** A subcategory follows its parent's scope change. */
    public void moveToScope(CategoryScope scope, String subject, Instant now) {
        this.scope = scope;
        touch(subject, now);
    }

    public void changeStatus(ResourceStatus target, String subject, Instant now) {
        this.status = target;
        this.archivedAt = target == ResourceStatus.ARCHIVED ? now : null;
        touch(subject, now);
    }

    private void touch(String subject, Instant now) {
        this.updatedBySubject = subject;
        this.updatedAt = now;
    }
}
