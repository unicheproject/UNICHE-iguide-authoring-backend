package eu.uniche.iguide.settings.application;

import eu.uniche.iguide.settings.infrastructure.ProjectSettingsRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Whether a capability is off for a Project ({@code 03} section 2): switched off in {@code featureConfiguration},
 * or not offered by the applied Template, whose capabilities are the map's only keys. A Project that is not
 * initialized has no settings and nothing is off. Callers run inside a transaction.
 */
@Component
public class CapabilityLookup {

    private final ProjectSettingsRepository settings;

    public CapabilityLookup(ProjectSettingsRepository settings) {
        this.settings = settings;
    }

    public boolean off(UUID projectId, String capability) {
        return settings.findById(projectId)
                .map(s -> !Boolean.TRUE.equals(s.getFeatureConfiguration().get(capability)))
                .orElse(false);
    }
}
