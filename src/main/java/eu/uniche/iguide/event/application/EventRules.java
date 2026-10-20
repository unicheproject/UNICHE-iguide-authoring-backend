package eu.uniche.iguide.event.application;

import eu.uniche.iguide.asset.application.ResourceMedia;
import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.asset.domain.ResourceAsset;
import eu.uniche.iguide.event.domain.Event;
import eu.uniche.iguide.event.domain.EventOccurrence;
import eu.uniche.iguide.event.domain.EventTranslation;
import eu.uniche.iguide.event.infrastructure.EventOccurrenceRepository;
import eu.uniche.iguide.event.infrastructure.EventRepository;
import eu.uniche.iguide.event.infrastructure.EventTranslationRepository;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.infrastructure.PlaceRepository;
import eu.uniche.iguide.platform.web.DuplicateValueException;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import eu.uniche.iguide.poi.infrastructure.PointOfInterestRepository;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.application.SlugGenerator;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Lookups and rules the Event use cases share. Every method takes the Project explicitly; callers have
 * already passed the Project access check.
 */
@Component
public class EventRules {

    public static final int MAX_OCCURRENCES = 500;
    public static final Duration MAX_OCCURRENCE_LENGTH = Duration.ofDays(366);
    static final int MAX_BOOKING_URL = 2048;

    private final EventRepository events;
    private final EventTranslationRepository translations;
    private final EventOccurrenceRepository occurrences;
    private final PlaceRepository places;
    private final PointOfInterestRepository pois;
    private final RouteRepository routes;
    private final ProjectLanguageLookup languages;
    private final List<EventReferenceSource> referenceSources;
    private final ResourceMedia media;

    public EventRules(EventRepository events, EventTranslationRepository translations,
                      EventOccurrenceRepository occurrences, PlaceRepository places, PointOfInterestRepository pois,
                      RouteRepository routes, ProjectLanguageLookup languages,
                      List<EventReferenceSource> referenceSources, ResourceMedia media) {
        this.events = events;
        this.translations = translations;
        this.occurrences = occurrences;
        this.places = places;
        this.pois = pois;
        this.routes = routes;
        this.languages = languages;
        this.referenceSources = referenceSources;
        this.media = media;
    }

    public Event requireEvent(UUID projectId, UUID eventId) {
        return events.findByProjectIdAndId(projectId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("No such event in this project."));
    }

    public EventOccurrence requireOccurrence(UUID projectId, UUID eventId, UUID occurrenceId) {
        return occurrences.findByProjectIdAndEventIdAndId(projectId, eventId, occurrenceId)
                .orElseThrow(() -> new ResourceNotFoundException("No such occurrence of this event."));
    }

    public EventView view(Event event) {
        return views(event.getProjectId(), List.of(event)).get(0);
    }

    /** Views of several Events of one Project, loaded with one query per table. */
    public List<EventView> views(UUID projectId, List<Event> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = list.stream().map(Event::getId).toList();
        Map<UUID, List<EventTranslation>> texts = translations.findByProjectIdAndEventIdIn(projectId, ids).stream()
                .collect(Collectors.groupingBy(EventTranslation::getEventId));
        Map<UUID, List<EventOccurrence>> times = occurrences.findByProjectIdAndEventIdInOrderByStartsAtAsc(projectId, ids)
                .stream().collect(Collectors.groupingBy(EventOccurrence::getEventId));
        Map<UUID, List<ResourceAsset>> mediaByEvent = media.of(projectId, AssetResourceType.EVENT, ids);
        return list.stream().map(e -> new EventView(e, texts.getOrDefault(e.getId(), List.of()),
                times.getOrDefault(e.getId(), List.of()), mediaByEvent.getOrDefault(e.getId(), List.of()))).toList();
    }

    /** Adds an error when the slug is malformed; throws {@code 409} when another Event uses it. */
    public void checkSlug(UUID projectId, String slug, UUID ownId, List<ErrorItem> errors) {
        if (!ResourceRules.checkSlugFormat(slug, errors)) {
            return;
        }
        boolean taken = ownId == null
                ? events.existsByProjectIdAndSlug(projectId, slug)
                : events.existsByProjectIdAndSlugAndIdNot(projectId, slug, ownId);
        if (taken) {
            throw new DuplicateValueException("slug", "Another event in this project already uses this slug.");
        }
    }

    public String generateSlug(UUID projectId, String title) {
        return SlugGenerator.generate(title, "event", slug -> events.existsByProjectIdAndSlug(projectId, slug));
    }

    public void checkLanguages(UUID projectId, Set<String> codes, List<ErrorItem> errors) {
        Set<String> enabled = languages.enabledCodes(projectId);
        codes.stream().filter(code -> !enabled.contains(code)).forEach(code -> errors.add(
                new ErrorItem("translations." + code, "UNKNOWN_LANGUAGE", "Is not a language of this project.")));
    }

    /** Each link is optional; a given one must be in this Project and not archived (DECISIONS.md D-023). */
    public void checkLinks(UUID projectId, UUID placeId, UUID poiId, UUID routeId, List<ErrorItem> errors) {
        checkLink(placeId == null ? null : places.findByProjectIdAndId(projectId, placeId).map(Place::getStatus),
                "placeId", errors);
        checkLink(poiId == null ? null : pois.findByProjectIdAndId(projectId, poiId).map(PointOfInterest::getStatus),
                "poiId", errors);
        checkLink(routeId == null ? null : routes.findByProjectIdAndId(projectId, routeId).map(Route::getStatus),
                "routeId", errors);
    }

    private static void checkLink(Optional<ResourceStatus> status, String path, List<ErrorItem> errors) {
        if (status == null) {
            return;
        }
        if (status.isEmpty()) {
            errors.add(new ErrorItem(path, "RESOURCE_NOT_FOUND", "No such resource in this project."));
        } else if (status.get() == ResourceStatus.ARCHIVED) {
            errors.add(new ErrorItem(path, "ARCHIVED_REFERENCE", "The resource is archived."));
        }
    }

    /** A blank booking URL is none; otherwise an absolute http or https address with a host. */
    public static String checkBookingUrl(String raw, List<ErrorItem> errors) {
        String url = ResourceRules.trimToNull(raw);
        if (url == null) {
            return null;
        }
        if (url.length() > MAX_BOOKING_URL) {
            errors.add(new ErrorItem("bookingUrl", "TOO_LONG", "At most " + MAX_BOOKING_URL + " characters."));
            return url;
        }
        try {
            URI uri = new URI(url);
            boolean web = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            if (!web || uri.getHost() == null) {
                errors.add(new ErrorItem("bookingUrl", "INVALID_FORMAT", "Must be an http or https address."));
            }
        } catch (URISyntaxException notAUri) {
            errors.add(new ErrorItem("bookingUrl", "INVALID_FORMAT", "Is not a valid address."));
        }
        return url;
    }

    /** Both ends required, the end after the start, at most 366 days long. */
    public static void checkTimes(Instant startsAt, Instant endsAt, List<ErrorItem> errors) {
        if (startsAt == null) {
            errors.add(new ErrorItem("startsAt", "REQUIRED", "Set when it starts."));
        }
        if (endsAt == null) {
            errors.add(new ErrorItem("endsAt", "REQUIRED", "Set when it ends."));
        }
        if (startsAt == null || endsAt == null) {
            return;
        }
        if (!endsAt.isAfter(startsAt)) {
            errors.add(new ErrorItem("endsAt", "BEFORE_START", "It must end after it starts."));
        } else if (Duration.between(startsAt, endsAt).compareTo(MAX_OCCURRENCE_LENGTH) > 0) {
            errors.add(new ErrorItem("endsAt", "TOO_LONG", "An occurrence lasts at most 366 days."));
        }
    }

    public List<ResourceReference> referencesTo(UUID projectId, UUID eventId) {
        return referenceSources.stream()
                .flatMap(source -> source.referencesTo(projectId, eventId).stream())
                .toList();
    }

    /** Events as references to another Resource, titled in the default language or by slug. */
    public List<ResourceReference> asReferences(UUID projectId, List<Event> using) {
        if (using.isEmpty()) {
            return List.of();
        }
        String defaultLanguage = languages.requireDefaultLanguage(projectId);
        Map<UUID, String> titles = translations.findByProjectIdAndEventIdIn(projectId,
                        using.stream().map(Event::getId).toList()).stream()
                .filter(t -> t.getLanguageCode().equals(defaultLanguage) && t.hasTitle())
                .collect(Collectors.toMap(EventTranslation::getEventId, EventTranslation::getTitle));
        return using.stream()
                .map(e -> new ResourceReference("EVENT", e.getId(), titles.getOrDefault(e.getId(), e.getSlug()), e.getStatus()))
                .toList();
    }
}
