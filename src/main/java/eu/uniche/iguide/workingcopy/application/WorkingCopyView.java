package eu.uniche.iguide.workingcopy.application;

import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.workingcopy.domain.ResourceWorkingCopy;
import java.util.List;

/**
 * A working copy with the Resource as it would read after apply ({@code resource}, the kind's normal read DTO),
 * and the live row's current version. When the payload no longer passes the rules, for example because a
 * referenced Resource was archived meanwhile, {@code resource} is {@code null} and {@code errors} says why.
 */
public record WorkingCopyView(ResourceWorkingCopy copy, long liveVersion, Object resource, List<ErrorItem> errors) {
}
