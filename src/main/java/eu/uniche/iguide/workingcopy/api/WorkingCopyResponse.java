package eu.uniche.iguide.workingcopy.api;

import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.workingcopy.application.WorkingCopyView;
import eu.uniche.iguide.workingcopy.domain.ResourceWorkingCopy;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A working copy. {@code payload} is the stored state; {@code resource} is the kind's read DTO as it would read
 * after apply, or {@code null} with {@code errors} when the payload no longer passes the rules.
 * {@code liveVersion} is the live row's version now, which status changes need; {@code version} is the copy's,
 * which the next save sends as {@code workingCopyVersion}. The last editor is a subject until Audit Events
 * exist (GAPS.md G-034).
 */
public record WorkingCopyResponse(UUID id, WorkingCopyResourceType resourceType, UUID resourceId, long baseVersion,
                                  long liveVersion, long version, Instant createdAt, Instant updatedAt,
                                  String createdBySubject, String updatedBySubject, Map<String, Object> payload,
                                  Object resource, List<ErrorItem> errors) {

    public static WorkingCopyResponse from(WorkingCopyView view) {
        ResourceWorkingCopy c = view.copy();
        return new WorkingCopyResponse(c.getId(), c.getResourceType(), c.getResourceId(), c.getBaseVersion(),
                view.liveVersion(), c.getVersion(), c.getCreatedAt(), c.getUpdatedAt(), c.getCreatedBySubject(),
                c.getUpdatedBySubject(), c.getPayload(), view.resource(), view.errors());
    }
}
