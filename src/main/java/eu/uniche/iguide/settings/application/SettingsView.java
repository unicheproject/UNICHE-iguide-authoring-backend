package eu.uniche.iguide.settings.application;

import eu.uniche.iguide.settings.domain.ProjectSettings;
import eu.uniche.iguide.template.domain.Capability;
import java.util.List;

/** The settings with the capabilities the applied Template declares. */
public record SettingsView(ProjectSettings settings, List<Capability> capabilities) {
}
