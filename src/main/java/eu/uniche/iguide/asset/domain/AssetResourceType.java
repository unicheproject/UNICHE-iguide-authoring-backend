package eu.uniche.iguide.asset.domain;

/**
 * The Resource kinds that bind Assets through {@code .../{kind}/{id}/resource-assets} (DECISIONS.md D-025).
 * Places joined on owner request (D-041); maps and Floor Plans have no media bindings, a Floor Plan names its
 * image directly.
 */
public enum AssetResourceType {
    PLACE("place"), POI("point of interest"), ROUTE("route"), GUIDE("guide"), EVENT("event");

    private final String label;

    AssetResourceType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
