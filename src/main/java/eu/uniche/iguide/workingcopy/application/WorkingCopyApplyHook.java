package eu.uniche.iguide.workingcopy.application;

import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.util.UUID;

/**
 * Something else a working copy carries to the live rows on apply, beside its payload; the translation provenance
 * of machine translations is the first (DECISIONS.md D-035). Called inside the apply transaction, after the payload
 * was written and before the copy is deleted. A discard needs no hook: what the copy carries goes with it.
 */
public interface WorkingCopyApplyHook {

    void applied(UUID projectId, WorkingCopyResourceType type, UUID resourceId);
}
