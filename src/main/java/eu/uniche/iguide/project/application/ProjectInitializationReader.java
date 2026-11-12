package eu.uniche.iguide.project.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What initialization chose for a Project, for {@code GET /projects/{id}} ({@code 12} section 3 Project picker;
 * DECISIONS.md D-040). Implemented by the initialization package, so this package does not depend on it. Callers
 * have passed the Project access check.
 */
public interface ProjectInitializationReader {

    ProjectInitialization read(UUID projectId);

    /**
     * @param initializedAt    {@code null} while the Project awaits initialization
     * @param template         the applied Template version, {@code null} before initialization
     * @param stylePresetKey   the theme's preset, {@code null} before initialization or when none is set
     * @param applicationStatus {@code DRAFT} before initialization ({@code 03} section 2: the initial value)
     * @param enabledSurfaces  the enabled surfaces in order; App settings may set them before initialization
     */
    record ProjectInitialization(Instant initializedAt, AppliedTemplate template, String stylePresetKey,
                                 String applicationStatus, List<String> enabledSurfaces) {

        public static ProjectInitialization none() {
            return new ProjectInitialization(null, null, null, null, List.of());
        }
    }

    /** One Template version; {@code name} is keyed by language code. */
    record AppliedTemplate(UUID id, String key, int version, Map<String, String> name) {
    }
}
