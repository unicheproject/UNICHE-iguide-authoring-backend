package eu.uniche.iguide.route.application;

/** Route-specific text of a stop in one language; a null {@code titleOverride} means the POI title. */
public record StopTranslationInput(String titleOverride, String description, String navigationInstructions,
                                   String narrative) {
}
