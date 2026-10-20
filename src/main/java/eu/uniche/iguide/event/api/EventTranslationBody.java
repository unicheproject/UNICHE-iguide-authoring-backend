package eu.uniche.iguide.event.api;

import eu.uniche.iguide.event.application.EventTranslationInput;
import eu.uniche.iguide.event.domain.EventTranslation;

/** The visitor-facing text of an Event in one language, in requests and responses alike. */
public record EventTranslationBody(String title, String summary, String description, String locationText) {

    public EventTranslationInput toInput() {
        return new EventTranslationInput(title, summary, description, locationText);
    }

    public static EventTranslationBody from(EventTranslation t) {
        return new EventTranslationBody(t.getTitle(), t.getSummary(), t.getDescription(), t.getLocationText());
    }
}
