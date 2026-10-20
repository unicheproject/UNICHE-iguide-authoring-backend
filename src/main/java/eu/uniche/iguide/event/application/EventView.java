package eu.uniche.iguide.event.application;

import eu.uniche.iguide.asset.domain.ResourceAsset;
import eu.uniche.iguide.event.domain.Event;
import eu.uniche.iguide.event.domain.EventOccurrence;
import eu.uniche.iguide.event.domain.EventTranslation;
import java.util.List;

/** An Event with its translations and its occurrences in start order. */
public record EventView(Event event, List<EventTranslation> translations, List<EventOccurrence> occurrences,
                        List<ResourceAsset> media) {
}
