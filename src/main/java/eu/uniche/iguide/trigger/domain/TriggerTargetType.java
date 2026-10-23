package eu.uniche.iguide.trigger.domain;

/**
 * The Resource kinds a trigger can open ({@code 03} section 14, DECISIONS.md D-026, D-028). A Route Stop has no
 * trigger of its own: the triggers of its Point of Interest open it.
 */
public enum TriggerTargetType {
    POI("point of interest", "pois"),
    PLACE("place", "places"),
    ROUTE("route", "routes"),
    GUIDE("guide", "guides"),
    EVENT("event", "events");

    private final String label;
    private final String runtimePath;

    TriggerTargetType(String label, String runtimePath) {
        this.label = label;
        this.runtimePath = runtimePath;
    }

    public String label() {
        return label;
    }

    /** The kind's segment of the runtime URL scheme, such as {@code /{lang}/routes/{slug}} ({@code 11} section 9). */
    public String runtimePath() {
        return runtimePath;
    }
}
