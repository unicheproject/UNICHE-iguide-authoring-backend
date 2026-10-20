package eu.uniche.iguide.event.api;

import eu.uniche.iguide.asset.api.AssetResponse;
import eu.uniche.iguide.event.application.EventView;
import eu.uniche.iguide.event.domain.Event;
import eu.uniche.iguide.event.domain.EventOccurrence;
import eu.uniche.iguide.event.domain.EventTranslation;
import eu.uniche.iguide.event.domain.OccurrenceStatus;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An Event with its translations and its occurrences in start order. {@code hasWorkingCopy} is always
 * false until working copies exist (GAPS.md G-027).
 */
public record EventResponse(UUID id, UUID projectId, String slug, ResourceStatus status, UUID placeId, UUID poiId,
                            UUID routeId, String bookingUrl, String internalName, boolean featured, int sortOrder,
                            Instant archivedAt, Instant createdAt, Instant updatedAt, long version,
                            boolean hasWorkingCopy, Instant workingCopyUpdatedAt,
                            Map<String, EventTranslationBody> translations, List<Occurrence> occurrences,
                            List<AssetResponse.Binding> media) {

    public record Occurrence(UUID id, UUID eventId, Instant startsAt, Instant endsAt, OccurrenceStatus status) {

        public static Occurrence from(EventOccurrence o) {
            return new Occurrence(o.getId(), o.getEventId(), o.getStartsAt(), o.getEndsAt(), o.getStatus());
        }
    }

    public static EventResponse from(EventView view) {
        Event e = view.event();
        Map<String, EventTranslationBody> texts = view.translations().stream()
                .collect(Collectors.toMap(EventTranslation::getLanguageCode, EventTranslationBody::from,
                        (a, b) -> a, TreeMap::new));
        return new EventResponse(e.getId(), e.getProjectId(), e.getSlug(), e.getStatus(), e.getPlaceId(), e.getPoiId(),
                e.getRouteId(), e.getBookingUrl(), e.getInternalName(), e.isFeatured(), e.getSortOrder(),
                e.getArchivedAt(), e.getCreatedAt(), e.getUpdatedAt(), e.getVersion(), false, null, texts,
                view.occurrences().stream().map(Occurrence::from).toList(),
                view.media().stream().map(AssetResponse.Binding::from).toList());
    }
}
