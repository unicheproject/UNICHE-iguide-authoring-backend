package eu.uniche.iguide.category.domain;

/** What a Category classifies: Points of Interest or Routes, never both ({@code 03} section 10). */
public enum CategoryScope {
    POI,
    ROUTE;

    public String label() {
        return this == POI ? "Points of Interest" : "Routes";
    }
}
