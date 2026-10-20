package eu.uniche.iguide.resource.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The lifecycle every authored Resource shares ({@code 03} section 1). {@code ACTIVE} means complete and
 * visible to the runtime; it is never called "published". Archival is soft.
 */
public enum ResourceStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED;

    private static final Map<ResourceStatus, Set<ResourceStatus>> ALLOWED = Map.of(
            DRAFT, EnumSet.of(ACTIVE, ARCHIVED),
            ACTIVE, EnumSet.of(DRAFT, ARCHIVED),
            ARCHIVED, EnumSet.of(DRAFT));

    public boolean canTransitionTo(ResourceStatus target) {
        return target != null && ALLOWED.get(this).contains(target);
    }

    /** Deactivation and archival take a Resource away from visitors, so references must be checked. */
    public boolean leavesVisitorsFor(ResourceStatus target) {
        return target == ARCHIVED || (this == ACTIVE && target == DRAFT);
    }
}
