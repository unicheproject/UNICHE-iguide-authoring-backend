package eu.uniche.iguide.event.application;

import eu.uniche.iguide.event.domain.Event;
import eu.uniche.iguide.event.domain.EventTranslation;
import eu.uniche.iguide.event.infrastructure.EventRepository;
import eu.uniche.iguide.event.infrastructure.EventTranslationRepository;
import eu.uniche.iguide.readiness.application.ReadinessCheck;
import eu.uniche.iguide.readiness.application.ReadinessContext;
import eu.uniche.iguide.readiness.application.ReadinessReport;
import eu.uniche.iguide.readiness.application.Texts;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Readiness of ACTIVE Events ({@code 03} sections 1 and 15; DECISIONS.md D-033): the title in the default language,
 * translations of the texts the CMS counts, and a Place, Point of Interest, or Route that is gone or archived.
 */
@Component
public class EventReadiness implements ReadinessCheck {

    static final List<String> FIELDS = List.of("title", "summary", "description");

    private final EventRepository events;
    private final EventTranslationRepository translations;

    public EventReadiness(EventRepository events, EventTranslationRepository translations) {
        this.events = events;
        this.translations = translations;
    }

    @Override
    public void check(ReadinessContext context, ReadinessReport report) {
        List<Event> active = events.findByProjectId(context.projectId()).stream()
                .filter(e -> e.getStatus() == ResourceStatus.ACTIVE).toList();
        if (active.isEmpty()) {
            return;
        }
        Map<UUID, Map<String, Map<String, String>>> texts = Texts.byOwner(
                translations.findByProjectIdAndEventIdIn(context.projectId(),
                        active.stream().map(Event::getId).toList()),
                EventTranslation::getEventId, EventTranslation::getLanguageCode,
                t -> Texts.of("title", t.getTitle(), "summary", t.getSummary(), "description", t.getDescription()));
        for (Event event : active) {
            String title = context.index().title("EVENT", event.getId());
            report.texts(context, "EVENT", event.getId(), title, texts.getOrDefault(event.getId(), Map.of()), FIELDS,
                    "title", List.of());
            dangling(context, report, event, title, "PLACE", event.getPlaceId(), "placeId");
            dangling(context, report, event, title, "POI", event.getPoiId(), "poiId");
            dangling(context, report, event, title, "ROUTE", event.getRouteId(), "routeId");
        }
    }

    private static void dangling(ReadinessContext context, ReadinessReport report, Event event, String title,
                                 String type, UUID target, String path) {
        if (target != null && context.index().dangling(type, target)) {
            report.block("EVENT", event.getId(), title, null, path, "DANGLING_REFERENCE");
        }
    }
}
