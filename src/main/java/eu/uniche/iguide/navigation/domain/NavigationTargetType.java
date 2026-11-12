package eu.uniche.iguide.navigation.domain;

/** The Resources a Navigation item can open: those with a detail screen in the visitor app ({@code 11} section 9). */
public enum NavigationTargetType {
    PLACE("place"),
    POI("point of interest"),
    ROUTE("route"),
    GUIDE("guide"),
    EVENT("event");

    private final String label;

    NavigationTargetType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
