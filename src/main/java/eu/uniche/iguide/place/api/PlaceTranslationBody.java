package eu.uniche.iguide.place.api;

import eu.uniche.iguide.place.application.PlaceTranslationInput;
import eu.uniche.iguide.place.domain.PlaceTranslation;

/** The visitor-facing text of a Place in one language, in requests and responses alike. */
public record PlaceTranslationBody(String title, String summary, String description, String visitorInformation,
                                   String accessibilityInformation) {

    public PlaceTranslationInput toInput() {
        return new PlaceTranslationInput(title, summary, description, visitorInformation, accessibilityInformation);
    }

    public static PlaceTranslationBody from(PlaceTranslation t) {
        return new PlaceTranslationBody(t.getTitle(), t.getSummary(), t.getDescription(), t.getVisitorInformation(),
                t.getAccessibilityInformation());
    }
}
