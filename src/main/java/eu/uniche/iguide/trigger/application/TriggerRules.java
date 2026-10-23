package eu.uniche.iguide.trigger.application;

import eu.uniche.iguide.platform.web.DuplicateValueException;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.event.domain.Event;
import eu.uniche.iguide.event.infrastructure.EventRepository;
import eu.uniche.iguide.guide.domain.Guide;
import eu.uniche.iguide.guide.infrastructure.GuideRepository;
import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.infrastructure.PlaceRepository;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import eu.uniche.iguide.poi.infrastructure.PointOfInterestRepository;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.trigger.domain.InteractionTrigger;
import eu.uniche.iguide.surface.domain.Surface;
import eu.uniche.iguide.trigger.domain.TriggerTargetType;
import eu.uniche.iguide.trigger.domain.TriggerType;
import eu.uniche.iguide.trigger.infrastructure.InteractionTriggerRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Validation of triggers ({@code 03} section 14, DECISIONS.md D-026). Every method takes the Project
 * explicitly; callers have already passed the Project access check.
 */
@Component
public class TriggerRules {

    static final Pattern CODE = Pattern.compile("^[A-Za-z0-9]+(-[A-Za-z0-9]+)*$");
    static final int MAX_CODE = 64;
    private static final String CODE_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    static final int DEFAULT_COOLDOWN = 300;
    static final BigDecimal DEFAULT_PROXIMITY = BigDecimal.valueOf(2);

    private static final Set<String> BEACON_KEYS = Set.of("uuid", "major", "minor", "proximityMeters", "cooldownSeconds");
    private static final Set<String> GEO_KEYS = Set.of("latitude", "longitude", "radiusMeters", "cooldownSeconds");

    private final InteractionTriggerRepository triggers;
    private final PointOfInterestRepository pois;
    private final PlaceRepository places;
    private final RouteRepository routes;
    private final GuideRepository guides;
    private final EventRepository events;

    public TriggerRules(InteractionTriggerRepository triggers, PointOfInterestRepository pois, PlaceRepository places,
                        RouteRepository routes, GuideRepository guides, EventRepository events) {
        this.triggers = triggers;
        this.pois = pois;
        this.places = places;
        this.routes = routes;
        this.guides = guides;
        this.events = events;
    }

    public InteractionTrigger requireTrigger(UUID projectId, UUID triggerId) {
        return triggers.findByProjectIdAndId(projectId, triggerId)
                .orElseThrow(() -> new ResourceNotFoundException("No such trigger in this project."));
    }

    /** The target is a Resource of this Project that is not archived. */
    public void checkTarget(UUID projectId, TriggerTargetType type, UUID targetId, List<ErrorItem> errors) {
        if (type == null) {
            errors.add(new ErrorItem("targetResourceType", "REQUIRED", "Choose what the trigger opens."));
            return;
        }
        if (targetId == null) {
            errors.add(new ErrorItem("targetResourceId", "REQUIRED", "Choose what the trigger opens."));
            return;
        }
        Optional<ResourceStatus> status = switch (type) {
            case POI -> pois.findByProjectIdAndId(projectId, targetId).map(PointOfInterest::getStatus);
            case PLACE -> places.findByProjectIdAndId(projectId, targetId).map(Place::getStatus);
            case ROUTE -> routes.findByProjectIdAndId(projectId, targetId).map(Route::getStatus);
            case GUIDE -> guides.findByProjectIdAndId(projectId, targetId).map(Guide::getStatus);
            case EVENT -> events.findByProjectIdAndId(projectId, targetId).map(Event::getStatus);
        };
        if (status.isEmpty()) {
            errors.add(new ErrorItem("targetResourceId", "RESOURCE_NOT_FOUND", "No such " + type.label() + " in this project."));
        } else if (status.get() == ResourceStatus.ARCHIVED) {
            errors.add(new ErrorItem("targetResourceId", "ARCHIVED_REFERENCE", "The " + type.label() + " is archived."));
        }
    }

    /** The target's slug, which its runtime URL carries ({@code 11} section 9); empty when the target is gone. */
    public Optional<String> targetSlug(UUID projectId, TriggerTargetType type, UUID targetId) {
        return switch (type) {
            case POI -> pois.findByProjectIdAndId(projectId, targetId).map(PointOfInterest::getSlug);
            case PLACE -> places.findByProjectIdAndId(projectId, targetId).map(Place::getSlug);
            case ROUTE -> routes.findByProjectIdAndId(projectId, targetId).map(Route::getSlug);
            case GUIDE -> guides.findByProjectIdAndId(projectId, targetId).map(Guide::getSlug);
            case EVENT -> events.findByProjectIdAndId(projectId, targetId).map(Event::getSlug);
        };
    }

    public static void checkType(TriggerType type, List<ErrorItem> errors) {
        if (type == null) {
            errors.add(new ErrorItem("triggerType", "REQUIRED", "Choose QR_CODE, BEACON, or GEO_PROXIMITY."));
        } else if (type == TriggerType.MANUAL) {
            errors.add(new ErrorItem("triggerType", "UNSUPPORTED_TYPE",
                    "Every resource is reachable by browsing; a MANUAL trigger needs no row."));
        } else if (!type.isAuthorable()) {
            errors.add(new ErrorItem("triggerType", "UNSUPPORTED_TYPE",
                    "Camera recognition is deferred and not part of the first release."));
        }
    }

    /**
     * Validates the editable part for {@code type} and returns it normalized, with defaults filled in. A
     * {@code QR_CODE} without a code gets a generated one; uniqueness conflicts are {@code 409}.
     */
    public InteractionTrigger.Settings normalize(UUID projectId, TriggerType type, String rawCode,
                                                 List<Surface> surfaces, Map<String, Object> rawConfiguration,
                                                 UUID ownId, List<ErrorItem> errors) {
        Map<String, Object> configuration = rawConfiguration == null ? Map.of() : rawConfiguration;
        List<Surface> checkedSurfaces = checkSurfaces(type, surfaces, errors);
        String code = rawCode == null ? null : rawCode.strip();
        if (code != null && code.isEmpty()) {
            code = null;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        switch (type) {
            case QR_CODE -> {
                if (!configuration.isEmpty()) {
                    errors.add(new ErrorItem("configuration", "NOT_ALLOWED", "A QR code has no configuration."));
                }
                if (code != null && (code.length() > MAX_CODE || !CODE.matcher(code).matches())) {
                    errors.add(new ErrorItem("code", "INVALID_FORMAT",
                            "Use Latin letters, digits, and single hyphens, at most " + MAX_CODE + " characters."));
                }
            }
            case BEACON -> {
                rejectCode(code, errors);
                rejectUnknownKeys(configuration, BEACON_KEYS, errors);
                normalized.put("uuid", uuid(configuration.get("uuid"), "configuration.uuid", errors));
                normalized.put("major", integer(configuration.get("major"), "configuration.major", 0, 65535, null, errors));
                normalized.put("minor", integer(configuration.get("minor"), "configuration.minor", 0, 65535, null, errors));
                normalized.put("proximityMeters", decimal(configuration.get("proximityMeters"), "configuration.proximityMeters",
                        BigDecimal.valueOf(0.5), BigDecimal.valueOf(50), DEFAULT_PROXIMITY, errors));
                normalized.put("cooldownSeconds", integer(configuration.get("cooldownSeconds"), "configuration.cooldownSeconds",
                        0, 86_400, DEFAULT_COOLDOWN, errors));
            }
            case GEO_PROXIMITY -> {
                rejectCode(code, errors);
                rejectUnknownKeys(configuration, GEO_KEYS, errors);
                normalized.put("latitude", decimal(configuration.get("latitude"), "configuration.latitude",
                        BigDecimal.valueOf(-90), BigDecimal.valueOf(90), null, errors));
                normalized.put("longitude", decimal(configuration.get("longitude"), "configuration.longitude",
                        BigDecimal.valueOf(-180), BigDecimal.valueOf(180), null, errors));
                normalized.put("radiusMeters", integer(configuration.get("radiusMeters"), "configuration.radiusMeters",
                        5, 5000, null, errors));
                normalized.put("cooldownSeconds", integer(configuration.get("cooldownSeconds"), "configuration.cooldownSeconds",
                        0, 86_400, DEFAULT_COOLDOWN, errors));
            }
            default -> throw new IllegalStateException("Unexpected trigger type " + type);
        }
        if (!errors.isEmpty()) {
            return null;
        }
        if (type == TriggerType.QR_CODE) {
            if (code == null) {
                code = generateCode(projectId);
            } else if (ownId == null ? triggers.existsByProjectIdAndCode(projectId, code)
                    : triggers.existsByProjectIdAndCodeAndIdNot(projectId, code, ownId)) {
                throw new DuplicateValueException("code", "Another trigger in this project already uses this code.");
            }
        }
        if (type == TriggerType.BEACON) {
            boolean taken = triggers.findByProjectIdAndTriggerTypeAndStatusNot(projectId, TriggerType.BEACON,
                            ResourceStatus.ARCHIVED).stream()
                    .filter(other -> !other.getId().equals(ownId))
                    .anyMatch(other -> sameBeacon(other.getConfiguration(), normalized));
            if (taken) {
                throw new DuplicateValueException("configuration.uuid",
                        "Another trigger in this project already uses this beacon (UUID, major, and minor).");
            }
        }
        return new InteractionTrigger.Settings(type == TriggerType.QR_CODE ? code : null, checkedSurfaces, normalized);
    }

    /** {@code 08} Milestone 5: a type is rejected for a surface that cannot run it ({@code 13} section 1). */
    static List<Surface> checkSurfaces(TriggerType type, List<Surface> surfaces, List<ErrorItem> errors) {
        if (surfaces == null) {
            return null;
        }
        if (surfaces.isEmpty() || surfaces.contains(null)) {
            errors.add(new ErrorItem("supportedSurfaces", "INVALID", "Leave empty for every surface, or name at least one."));
            return null;
        }
        Set<Surface> distinct = new LinkedHashSet<>(surfaces);
        distinct.stream().filter(s -> !type.runsOn(s)).forEach(s -> errors.add(new ErrorItem("supportedSurfaces",
                "UNSUPPORTED_SURFACE", "A " + type + " trigger cannot run on " + s + ".")));
        return new ArrayList<>(distinct);
    }

    private String generateCode(UUID projectId) {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            if (!triggers.existsByProjectIdAndCode(projectId, code.toString())) {
                return code.toString();
            }
        }
        throw new IllegalStateException("No free trigger code after 20 attempts");
    }

    private static boolean sameBeacon(Map<String, Object> a, Map<String, Object> b) {
        return String.valueOf(a.get("uuid")).equalsIgnoreCase(String.valueOf(b.get("uuid")))
                && Objects.equals(asLong(a.get("major")), asLong(b.get("major")))
                && Objects.equals(asLong(a.get("minor")), asLong(b.get("minor")));
    }

    private static Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static void rejectCode(String code, List<ErrorItem> errors) {
        if (code != null) {
            errors.add(new ErrorItem("code", "NOT_ALLOWED", "Only a QR code has a code."));
        }
    }

    private static void rejectUnknownKeys(Map<String, Object> configuration, Set<String> allowed, List<ErrorItem> errors) {
        configuration.keySet().stream().filter(k -> !allowed.contains(k)).sorted().forEach(k ->
                errors.add(new ErrorItem("configuration." + k, "NOT_ALLOWED", "Is not a setting of this trigger type.")));
    }

    private static String uuid(Object value, String path, List<ErrorItem> errors) {
        if (value == null || String.valueOf(value).isBlank()) {
            errors.add(new ErrorItem(path, "REQUIRED", "Is required."));
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value).strip()).toString().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            errors.add(new ErrorItem(path, "INVALID_FORMAT", "Must be a UUID such as e2c56db5-dffb-48d2-b060-d0f5a71096e0."));
            return null;
        }
    }

    private static Integer integer(Object value, String path, int min, int max, Integer fallback, List<ErrorItem> errors) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            if (fallback == null) {
                errors.add(new ErrorItem(path, "REQUIRED", "Is required."));
            }
            return fallback;
        }
        BigDecimal number = number(value);
        if (number == null || number.stripTrailingZeros().scale() > 0) {
            errors.add(new ErrorItem(path, "INVALID", "Must be a whole number."));
            return null;
        }
        if (number.compareTo(BigDecimal.valueOf(min)) < 0 || number.compareTo(BigDecimal.valueOf(max)) > 0) {
            errors.add(new ErrorItem(path, "OUT_OF_RANGE", "Must be between " + min + " and " + max + "."));
            return null;
        }
        return number.intValueExact();
    }

    private static BigDecimal decimal(Object value, String path, BigDecimal min, BigDecimal max, BigDecimal fallback,
                                      List<ErrorItem> errors) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            if (fallback == null) {
                errors.add(new ErrorItem(path, "REQUIRED", "Is required."));
            }
            return fallback;
        }
        BigDecimal number = number(value);
        if (number == null) {
            errors.add(new ErrorItem(path, "INVALID", "Must be a number."));
            return null;
        }
        if (number.compareTo(min) < 0 || number.compareTo(max) > 0) {
            errors.add(new ErrorItem(path, "OUT_OF_RANGE", "Must be between " + min.stripTrailingZeros().toPlainString()
                    + " and " + max.stripTrailingZeros().toPlainString() + "."));
            return null;
        }
        return number;
    }

    private static BigDecimal number(Object value) {
        try {
            return value instanceof Number n ? new BigDecimal(n.toString()) : new BigDecimal(String.valueOf(value).strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
