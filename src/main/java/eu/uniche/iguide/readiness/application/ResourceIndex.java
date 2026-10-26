package eu.uniche.iguide.readiness.application;

import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The status and display title of every Resource of the Project, by readiness type ({@code POI}, {@code PLACE},
 * {@code ROUTE}, {@code GUIDE}, {@code EVENT}, {@code CATEGORY}, {@code MAP}, {@code FLOOR_PLAN}), so the checks can
 * tell a dangling reference and name a Resource without a query each.
 */
public class ResourceIndex {

    private record Entry(ResourceStatus status, String title) {
    }

    private final Map<String, Map<UUID, Entry>> byType = new HashMap<>();

    void put(String type, UUID id, ResourceStatus status, String title) {
        byType.computeIfAbsent(type, t -> new HashMap<>()).put(id, new Entry(status, title));
    }

    /** {@code null} when no Resource of that type has the id in the Project. */
    public ResourceStatus status(String type, UUID id) {
        Entry entry = id == null ? null : byType.getOrDefault(type, Map.of()).get(id);
        return entry == null ? null : entry.status();
    }

    public String title(String type, UUID id) {
        Entry entry = id == null ? null : byType.getOrDefault(type, Map.of()).get(id);
        return entry == null ? null : entry.title();
    }

    /** A reference visitors cannot follow: the target is missing from the Project or archived. */
    public boolean dangling(String type, UUID id) {
        ResourceStatus status = status(type, id);
        return status == null || status == ResourceStatus.ARCHIVED;
    }
}
