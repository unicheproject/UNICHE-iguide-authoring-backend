package eu.uniche.iguide.settings.api;

import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.settings.application.GetProjectSettingsQuery;
import eu.uniche.iguide.settings.application.SettingsView;
import eu.uniche.iguide.settings.application.UpdateProjectSettingsUseCase;
import eu.uniche.iguide.settings.domain.ApplicationStatus;
import eu.uniche.iguide.template.domain.Capability;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Project settings ({@code 03} section 2, {@code 07} section 14; DECISIONS.md D-040). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/settings")
public class ProjectSettingsController {

    /**
     * @param capabilities the applied Template's capabilities: the only keys {@code featureConfiguration} takes
     */
    public record SettingsResponse(ApplicationStatus applicationStatus, long contentRevision, String timezone,
                                   Map<String, Boolean> featureConfiguration, List<Capability> capabilities,
                                   long version, Instant updatedAt) {

        static SettingsResponse from(SettingsView view) {
            var s = view.settings();
            return new SettingsResponse(s.getApplicationStatus(), s.getContentRevision(), s.getTimezone(),
                    s.getFeatureConfiguration(), view.capabilities(), s.getVersion(), s.getUpdatedAt());
        }
    }

    /** Listed capabilities are set, unlisted ones keep their value; {@code timezone} null clears it. */
    public record UpdateSettingsRequest(Map<String, Boolean> featureConfiguration, String timezone, Long version) {
    }

    private final GetProjectSettingsQuery getSettings;
    private final UpdateProjectSettingsUseCase updateSettings;

    public ProjectSettingsController(GetProjectSettingsQuery getSettings, UpdateProjectSettingsUseCase updateSettings) {
        this.getSettings = getSettings;
        this.updateSettings = updateSettings;
    }

    @GetMapping
    public SettingsResponse get(@PathVariable UUID projectId) {
        return SettingsResponse.from(getSettings.execute(projectId, CurrentPrincipal.require()));
    }

    @PutMapping
    public SettingsResponse update(@PathVariable UUID projectId, @RequestBody UpdateSettingsRequest request) {
        return SettingsResponse.from(updateSettings.execute(projectId, CurrentPrincipal.require(),
                request.featureConfiguration(), request.timezone(), request.version()));
    }
}
