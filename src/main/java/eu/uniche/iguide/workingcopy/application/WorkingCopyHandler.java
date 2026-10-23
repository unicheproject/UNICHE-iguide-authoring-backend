package eu.uniche.iguide.workingcopy.application;

import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.time.Instant;
import java.util.UUID;

/**
 * What the working copy needs from one Resource kind. Each kind implements it in its own package, reusing the
 * rules of its normal save, so a payload is validated exactly like a save of a DRAFT ({@code 03} section 1).
 * Every method runs inside a transaction opened by the caller, after the Project access check.
 *
 * @param <S> the payload record: the kind's update body without {@code version}, plus its child rows
 */
public interface WorkingCopyHandler<S> {

    WorkingCopyResourceType type();

    Class<S> stateType();

    /** Reads the live row for update; {@code 404} when the Resource is not in the Project. */
    LiveResource lock(UUID projectId, UUID resourceId);

    /**
     * Writes the whole state onto the live rows: fields, translations, and children, which are replaced to match
     * the payload. Errors carry payload paths. No status or version check: the caller has done both.
     */
    void write(UUID projectId, UUID resourceId, UnichePrincipal principal, S state);

    /** The kind's read DTO of the rows as they now are, with the given working copy timestamp. */
    Object render(UUID projectId, UUID resourceId, Instant workingCopyUpdatedAt);
}
