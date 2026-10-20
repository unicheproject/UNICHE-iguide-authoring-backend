package eu.uniche.iguide.place.domain;

/**
 * How a Place is mapped ({@code 03} section 5): an {@code OUTDOOR} Place links to one Geographic Map, an
 * {@code INDOOR} Place to Floor Plans. The link is stored on the map or plan.
 */
public enum PlaceEnvironment {
    INDOOR,
    OUTDOOR
}
