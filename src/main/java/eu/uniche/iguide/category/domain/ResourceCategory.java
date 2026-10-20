package eu.uniche.iguide.category.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A Point of Interest or Route in a Category ({@code 03} section 10). The Resource is a generic reference
 * checked by the service; a Resource is in at most one Category (DECISIONS.md D-024).
 */
@Entity
@Table(name = "resource_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResourceCategory {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, updatable = false)
    private CategoryScope resourceType;

    @Column(name = "resource_id", nullable = false, updatable = false)
    private UUID resourceId;

    @Column(name = "sort_order")
    private Integer sortOrder;

    public static ResourceCategory of(UUID projectId, UUID categoryId, CategoryScope resourceType, UUID resourceId) {
        ResourceCategory binding = new ResourceCategory();
        binding.projectId = projectId;
        binding.categoryId = categoryId;
        binding.resourceType = resourceType;
        binding.resourceId = resourceId;
        return binding;
    }
}
