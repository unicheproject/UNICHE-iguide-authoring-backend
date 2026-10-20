package eu.uniche.iguide.poi.api;

import eu.uniche.iguide.poi.application.PoiTranslationInput;
import eu.uniche.iguide.poi.domain.PoiTranslation;

/** The visitor-facing text of a Point of Interest in one language, in requests and responses alike. */
public record PoiTranslationBody(String title, String summary, String description, String instructions,
                                 String accessibilityInformation) {

    public PoiTranslationInput toInput() {
        return new PoiTranslationInput(title, summary, description, instructions, accessibilityInformation);
    }

    public static PoiTranslationBody from(PoiTranslation t) {
        return new PoiTranslationBody(t.getTitle(), t.getSummary(), t.getDescription(), t.getInstructions(),
                t.getAccessibilityInformation());
    }
}
