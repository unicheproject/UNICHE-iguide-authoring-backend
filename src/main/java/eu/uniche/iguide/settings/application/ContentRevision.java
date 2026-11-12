package eu.uniche.iguide.settings.application;

import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.settings.infrastructure.ProjectSettingsRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * {@code ProjectSettings.contentRevision} ({@code 03} section 2, {@code 11} section 4): raised by one, in the
 * caller's transaction, on every mutation that changes what a visitor could see. It is never raised by a save into a
 * working copy, by an edit of a DRAFT Resource, or by the passage of time. One SQL increment, so concurrent
 * mutations never lose a step and the settings' own {@code version} does not move. A Project that is not initialized
 * has no settings and so no revision (DECISIONS.md D-042).
 */
@Service
public class ContentRevision {

    private final ProjectSettingsRepository settings;

    public ContentRevision(ProjectSettingsRepository settings) {
        this.settings = settings;
    }

    public void increment(UUID projectId) {
        settings.incrementContentRevision(projectId);
    }

    /** A lifecycle transition counts when the Resource becomes visible or stops being visible. */
    public void afterTransition(UUID projectId, ResourceStatus from, ResourceStatus to) {
        if (from == ResourceStatus.ACTIVE || to == ResourceStatus.ACTIVE) {
            increment(projectId);
        }
    }
}
