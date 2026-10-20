package eu.uniche.iguide.route.api;

import eu.uniche.iguide.route.application.RouteTranslationInput;
import eu.uniche.iguide.route.domain.RouteTranslation;

/** The visitor-facing text of a Route in one language, in requests and responses alike. */
public record RouteTranslationBody(String title, String summary, String description, String instructions) {

    public RouteTranslationInput toInput() {
        return new RouteTranslationInput(title, summary, description, instructions);
    }

    public static RouteTranslationBody from(RouteTranslation t) {
        return new RouteTranslationBody(t.getTitle(), t.getSummary(), t.getDescription(), t.getInstructions());
    }
}
