package eu.uniche.iguide.route.api;

import eu.uniche.iguide.route.application.StopTranslationInput;
import eu.uniche.iguide.route.domain.RouteStopTranslation;

/** Route-specific text of a stop in one language; a null {@code titleOverride} means the POI title. */
public record StopTranslationBody(String titleOverride, String description, String navigationInstructions,
                                  String narrative) {

    public StopTranslationInput toInput() {
        return new StopTranslationInput(titleOverride, description, navigationInstructions, narrative);
    }

    public static StopTranslationBody from(RouteStopTranslation t) {
        return new StopTranslationBody(t.getTitleOverride(), t.getDescription(), t.getNavigationInstructions(),
                t.getNarrative());
    }
}
