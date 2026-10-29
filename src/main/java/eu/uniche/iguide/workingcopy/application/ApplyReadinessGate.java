package eu.uniche.iguide.workingcopy.application;

import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.readiness.application.GetReadinessQuery;
import eu.uniche.iguide.readiness.application.ReadinessIssue;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Blocking readiness issues prevent the application of a working copy ({@code 14} section 2; DECISIONS.md D-037).
 * Read together with "apply validates the payload like a normal save" ({@code 03} section 1): an apply is refused
 * only when it adds blocking issues the Project did not have, so an apply may fix issues and unrelated ones never
 * trap an author. Issues are compared as multisets keyed by Resource, language, code, and path with list indexes
 * dropped, so moving a stop does not make its existing finding new, while a second finding of the same kind is.
 * Runs inside the apply's transaction; the refusal rolls the write back and the copy stays.
 */
@Component
public class ApplyReadinessGate {

    private final GetReadinessQuery readiness;

    public ApplyReadinessGate(GetReadinessQuery readiness) {
        this.readiness = readiness;
    }

    /** The blocking issues of the Project as they are now, to compare with after the write. */
    public List<ReadinessIssue> blocking(UUID projectId) {
        return readiness.evaluate(projectId).blocking();
    }

    /** Refuses with {@code 422} when the Project now has blocking issues {@code before} did not have. */
    public void requireNoNewBlocking(UUID projectId, WorkingCopyResourceType type, UUID resourceId,
                                     List<ReadinessIssue> before) {
        List<ReadinessIssue> added = added(before, blocking(projectId));
        if (added.isEmpty()) {
            return;
        }
        String own = readinessType(type);
        List<ErrorItem> errors = added.stream().map(issue -> own.equals(issue.resourceType())
                        && resourceId.equals(issue.resourceId())
                        ? new ErrorItem(issue.path(), issue.code(), message(issue.code()))
                        : new ErrorItem("", issue.code(), elsewhere(issue)))
                .toList();
        throw new ValidationFailedException(
                "Applying would leave the " + type.label() + " not ready for visitors.", errors);
    }

    /** The issues of {@code after} beyond those of {@code before}, counted per normalized key. */
    static List<ReadinessIssue> added(List<ReadinessIssue> before, List<ReadinessIssue> after) {
        Map<Key, Integer> remaining = new HashMap<>();
        before.forEach(issue -> remaining.merge(Key.of(issue), 1, Integer::sum));
        List<ReadinessIssue> added = new ArrayList<>();
        for (ReadinessIssue issue : after) {
            Key key = Key.of(issue);
            int left = remaining.getOrDefault(key, 0);
            if (left > 0) {
                remaining.put(key, left - 1);
            } else {
                added.add(issue);
            }
        }
        return added;
    }

    /** The readiness evaluation names maps {@code MAP}; every other kind by its own name. */
    static String readinessType(WorkingCopyResourceType type) {
        return type == WorkingCopyResourceType.GEOGRAPHIC_MAP ? "MAP" : type.name();
    }

    private static String message(String code) {
        return switch (code) {
            case "ROUTE_WITHOUT_ACTIVE_STOP" -> "At least one stop must be an ACTIVE point of interest.";
            case "MISSING_DEFAULT_LANGUAGE_VALUE" -> "Is required in the default language.";
            case "DANGLING_REFERENCE" -> "Points to something visitors cannot see.";
            case "MISSING_ALTERNATIVE_TEXT" ->
                    "An image needs alternative text in the default language, or must be marked decorative.";
            default -> "Must be fixed before visitors can see the change.";
        };
    }

    private static String elsewhere(ReadinessIssue issue) {
        String what = issue.title() == null ? issue.resourceType().toLowerCase().replace('_', ' ') : issue.title();
        return "Would make " + what + " not ready for visitors: " + message(issue.code());
    }

    private record Key(String resourceType, UUID resourceId, String languageCode, String code, String path) {

        static Key of(ReadinessIssue issue) {
            String path = issue.path() == null ? null : issue.path().replaceAll("\\[\\d+]", "[]");
            return new Key(issue.resourceType(), issue.resourceId(), issue.languageCode(), issue.code(),
                    Objects.requireNonNullElse(path, ""));
        }
    }
}
