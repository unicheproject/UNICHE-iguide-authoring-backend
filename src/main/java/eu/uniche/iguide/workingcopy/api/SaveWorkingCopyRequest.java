package eu.uniche.iguide.workingcopy.api;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * {@code PUT .../{kind}/{id}/working-copy}. {@code baseVersion} is the live row's version the edit started from;
 * {@code workingCopyVersion} is the copy's version when one was loaded, {@code null} for the first save.
 * {@code payload} is the kind's update body without {@code version}, plus its child rows (DECISIONS.md D-027).
 */
public record SaveWorkingCopyRequest(@NotNull Long baseVersion, Long workingCopyVersion,
                                     @NotNull Map<String, Object> payload) {
}
