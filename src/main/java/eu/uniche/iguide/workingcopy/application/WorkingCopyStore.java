package eu.uniche.iguide.workingcopy.application;

import eu.uniche.iguide.workingcopy.domain.ResourceWorkingCopy;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import eu.uniche.iguide.workingcopy.infrastructure.ResourceWorkingCopyRepository;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * What the Resource kinds need to know about working copies: whether one exists ({@code hasWorkingCopy},
 * {@code workingCopyUpdatedAt}, {@code pending=true}, {@code 07} section 14), and dropping it when the Resource
 * leaves ACTIVE ({@code 03} section 1). Callers have passed the Project access check.
 */
@Component
public class WorkingCopyStore {

    private final ResourceWorkingCopyRepository copies;

    public WorkingCopyStore(ResourceWorkingCopyRepository copies) {
        this.copies = copies;
    }

    /** The time of the Resource's working copy, or {@code null} when it has none. */
    public Instant updatedAt(UUID projectId, WorkingCopyResourceType type, UUID resourceId) {
        return copies.findByProjectIdAndResourceTypeAndResourceId(projectId, type, resourceId)
                .map(ResourceWorkingCopy::getUpdatedAt)
                .orElse(null);
    }

    /** The working copy time of each Resource that has one; the others are absent. */
    public Map<UUID, Instant> updatedAt(UUID projectId, WorkingCopyResourceType type, Collection<UUID> resourceIds) {
        if (resourceIds.isEmpty()) {
            return Map.of();
        }
        return copies.findByProjectIdAndResourceTypeAndResourceIdIn(projectId, type, resourceIds).stream()
                .collect(Collectors.toMap(ResourceWorkingCopy::getResourceId, ResourceWorkingCopy::getUpdatedAt));
    }

    /** Deactivation and archival discard the copy in the same transaction ({@code 06} section 17). */
    public void dropFor(UUID projectId, WorkingCopyResourceType type, UUID resourceId) {
        copies.deleteFor(projectId, type, resourceId);
    }

    /** Restricts a Resource query to the rows that have a working copy ({@code pending=true}). */
    public static <T> Specification<T> pending(UUID projectId, WorkingCopyResourceType type) {
        return (root, query, cb) -> {
            Subquery<UUID> copy = query.subquery(UUID.class);
            var c = copy.from(ResourceWorkingCopy.class);
            copy.select(c.get("resourceId")).where(
                    cb.equal(c.get("projectId"), projectId),
                    cb.equal(c.get("resourceType"), type),
                    cb.equal(c.get("resourceId"), root.get("id")));
            return cb.exists(copy);
        };
    }
}
