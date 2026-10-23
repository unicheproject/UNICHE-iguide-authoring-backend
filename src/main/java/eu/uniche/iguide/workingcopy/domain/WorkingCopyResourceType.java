package eu.uniche.iguide.workingcopy.domain;

import java.util.Arrays;
import java.util.Optional;

/** The Resource kinds edited through a working copy, with the collection segment of their paths. */
public enum WorkingCopyResourceType {
    PLACE("places", "place"),
    POI("pois", "point of interest"),
    ROUTE("routes", "route"),
    GUIDE("guides", "guide"),
    EVENT("events", "event"),
    CATEGORY("categories", "category"),
    GEOGRAPHIC_MAP("maps", "map"),
    FLOOR_PLAN("floor-plans", "floor plan");

    private final String pathSegment;
    private final String label;

    WorkingCopyResourceType(String pathSegment, String label) {
        this.pathSegment = pathSegment;
        this.label = label;
    }

    public String pathSegment() {
        return pathSegment;
    }

    public String label() {
        return label;
    }

    public static Optional<WorkingCopyResourceType> fromPathSegment(String segment) {
        return Arrays.stream(values()).filter(type -> type.pathSegment.equals(segment)).findFirst();
    }
}
