package eu.uniche.iguide.navigation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One entry of a Navigation ({@code 03} section 13), ordered among the items that share its parent. PRIMARY
 * items may nest under {@code parentItemId}; FOOTER items are flat.
 */
@Entity
@Table(name = "navigation_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NavigationItem {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "navigation_id", nullable = false, updatable = false)
    private UUID navigationId;

    @Column(name = "parent_item_id")
    private UUID parentItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private NavigationItemType itemType;

    @Column(name = "target_page_id")
    private UUID targetPageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_resource_type")
    private NavigationTargetType targetResourceType;

    @Column(name = "target_resource_id")
    private UUID targetResourceId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> configuration = new HashMap<>();

    @Version
    @Column(nullable = false)
    private long version;

    public static NavigationItem of(UUID projectId, UUID navigationId, UUID parentItemId, int sortOrder) {
        NavigationItem item = new NavigationItem();
        item.projectId = projectId;
        item.navigationId = navigationId;
        item.parentItemId = parentItemId;
        item.sortOrder = sortOrder;
        return item;
    }

    /** What the item opens: a Resource, or nothing for a GROUP heading. */
    public void target(NavigationItemType itemType, NavigationTargetType targetType, UUID targetId) {
        this.itemType = itemType;
        this.targetResourceType = itemType == NavigationItemType.RESOURCE ? targetType : null;
        this.targetResourceId = itemType == NavigationItemType.RESOURCE ? targetId : null;
        this.targetPageId = null;
    }

    public void moveTo(UUID parentItemId, int sortOrder) {
        this.parentItemId = parentItemId;
        this.sortOrder = sortOrder;
    }
}
