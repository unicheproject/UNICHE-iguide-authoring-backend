package eu.uniche.iguide.resource.application;

import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.platform.web.VersionConflictException;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Field rules shared by every authored Resource kind. Error messages never repeat the rejected value.
 */
public final class ResourceRules {

    public static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    public static final int MAX_SLUG = 200;
    public static final int MAX_NAME = 255;

    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

    private ResourceRules() {
    }

    /** Adds an error when the slug is malformed; uniqueness is checked by each kind against its table. */
    public static boolean checkSlugFormat(String slug, List<ErrorItem> errors) {
        if (slug == null || slug.length() > MAX_SLUG || !SLUG.matcher(slug).matches()) {
            errors.add(new ErrorItem("slug", "INVALID_FORMAT",
                    "Use lowercase Latin letters, digits, and single hyphens, at most " + MAX_SLUG + " characters."));
            return false;
        }
        return true;
    }

    /** Optional coordinates: both or neither, each in range. */
    public static void checkOptionalCoordinates(BigDecimal latitude, BigDecimal longitude, String prefix,
                                                List<ErrorItem> errors) {
        if ((latitude == null) != (longitude == null)) {
            errors.add(new ErrorItem(prefix + "latitude", "INCOMPLETE",
                    "Latitude and longitude are set together or not at all."));
            return;
        }
        if (latitude != null) {
            checkLatitude(latitude, prefix + "latitude", errors);
            checkLongitude(longitude, prefix + "longitude", errors);
        }
    }

    public static void checkLatitude(BigDecimal latitude, String path, List<ErrorItem> errors) {
        if (latitude == null) {
            errors.add(new ErrorItem(path, "REQUIRED", "Is required."));
        } else if (latitude.abs().compareTo(MAX_LATITUDE) > 0) {
            errors.add(new ErrorItem(path, "OUT_OF_RANGE", "Must be between -90 and 90."));
        }
    }

    public static void checkLongitude(BigDecimal longitude, String path, List<ErrorItem> errors) {
        if (longitude == null) {
            errors.add(new ErrorItem(path, "REQUIRED", "Is required."));
        } else if (longitude.abs().compareTo(MAX_LONGITUDE) > 0) {
            errors.add(new ErrorItem(path, "OUT_OF_RANGE", "Must be between -180 and 180."));
        }
    }

    /** A required author-side name, trimmed; returns the stored value or adds an error. */
    public static String requireName(String value, String path, List<ErrorItem> errors) {
        String name = trimToNull(value);
        if (name == null) {
            errors.add(new ErrorItem(path, "REQUIRED", "Is required."));
        } else if (name.length() > MAX_NAME) {
            errors.add(new ErrorItem(path, "TOO_LONG", "At most " + MAX_NAME + " characters."));
        }
        return name;
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static void throwIfAny(List<ErrorItem> errors, String message) {
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(message, errors);
        }
    }

    /**
     * The version the caller last read must still be current ({@code 06} section 16). JPA's own
     * {@code @Version} check still guards the race between this read and the write.
     */
    public static void requireCurrentVersion(long current, Long expected, String kind) {
        if (expected == null || current != expected) {
            throw new VersionConflictException("The " + kind + " was changed since it was loaded.", current);
        }
    }

    /**
     * Only a {@code DRAFT} Resource is edited directly. An {@code ACTIVE} one is edited through its working copy
     * (AGENTS.md section 4 rule 13, {@code PUT .../working-copy}, DECISIONS.md D-027); an {@code ARCHIVED} one is
     * restored first.
     */
    public static void requireDraft(ResourceStatus status, String kind) {
        if (status == ResourceStatus.ACTIVE) {
            throw new ValidationFailedException("An ACTIVE " + kind + " is edited through its working copy.",
                    List.of(new ErrorItem("status", "RESOURCE_NOT_EDITABLE",
                            "Save the changes with PUT .../working-copy and apply them.")));
        }
        if (status != ResourceStatus.DRAFT) {
            throw new ValidationFailedException("Only a DRAFT " + kind + " can be edited directly.",
                    List.of(new ErrorItem("status", "RESOURCE_NOT_EDITABLE",
                            "Restore the " + kind + " to DRAFT before editing it.")));
        }
    }

    public static void requireTransition(ResourceStatus current, ResourceStatus target, String kind) {
        if (!current.canTransitionTo(target)) {
            throw new ValidationFailedException("A " + current + " " + kind + " cannot become " + target + ".",
                    List.of(new ErrorItem("targetStatus", "ILLEGAL_TRANSITION", "Not allowed from " + current + ".")));
        }
    }
}
