package eu.uniche.iguide.place.domain;

/** The kinds of Place the specification suggests ({@code 03} section 5). */
public enum PlaceType {
    REGION,
    DESTINATION,
    SITE,
    BUILDING,
    FLOOR,
    ROOM,
    AREA,
    OTHER;

    /** The environment a new Place of this type most likely has, when the author does not choose one. */
    public PlaceEnvironment defaultEnvironment() {
        return switch (this) {
            case BUILDING, FLOOR, ROOM -> PlaceEnvironment.INDOOR;
            default -> PlaceEnvironment.OUTDOOR;
        };
    }
}
