package eu.uniche.iguide.event.application;

import eu.uniche.iguide.event.infrastructure.EventRepository;
import eu.uniche.iguide.event.infrastructure.EventTranslationRepository;
import eu.uniche.iguide.language.application.TitledResourceCounter;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Events' share of the translation completeness counts. */
@Component
public class EventTitleCounter implements TitledResourceCounter {

    private final EventRepository events;
    private final EventTranslationRepository translations;

    public EventTitleCounter(EventRepository events, EventTranslationRepository translations) {
        this.events = events;
        this.translations = translations;
    }

    @Override
    public long countResources(UUID projectId) {
        return events.countByProjectIdAndStatusNot(projectId, ResourceStatus.ARCHIVED);
    }

    @Override
    public long countTitled(UUID projectId, String languageCode) {
        return translations.countTitled(projectId, languageCode, ResourceStatus.ARCHIVED);
    }
}
