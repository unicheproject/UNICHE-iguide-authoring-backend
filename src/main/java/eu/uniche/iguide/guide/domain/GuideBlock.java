package eu.uniche.iguide.guide.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One block of a Guide at a 0-based position ({@code 03} section 11): {@code TEXT} carries rich text per
 * language, {@code REFERENCE} points at a Resource of the same Project and carries a caption per
 * language. The target is a generic reference, checked by the service ({@code 06} section 5).
 */
@Entity
@Table(name = "guide_blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuideBlock {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "guide_id", nullable = false, updatable = false)
    private UUID guideId;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", nullable = false, updatable = false)
    private GuideBlockType blockType;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type")
    private GuideTargetType resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> configuration = new HashMap<>();

    public static GuideBlock of(UUID projectId, UUID guideId, GuideBlockType blockType, GuideTargetType resourceType,
                                UUID resourceId, int sortOrder) {
        GuideBlock block = new GuideBlock();
        block.projectId = projectId;
        block.guideId = guideId;
        block.blockType = blockType;
        block.sortOrder = sortOrder;
        block.target(resourceType, resourceId);
        return block;
    }

    public void target(GuideTargetType resourceType, UUID resourceId) {
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public void moveTo(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
