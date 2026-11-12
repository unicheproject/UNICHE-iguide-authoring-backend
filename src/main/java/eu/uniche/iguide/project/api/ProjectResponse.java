package eu.uniche.iguide.project.api;

import eu.uniche.iguide.platform.web.LocalizedText;
import eu.uniche.iguide.project.application.ProjectInitializationReader.AppliedTemplate;
import eu.uniche.iguide.project.application.ProjectInitializationReader.ProjectInitialization;
import eu.uniche.iguide.project.application.ProjectView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * This API's representation of a Project.
 *
 * <p>{@code name}, {@code slug}, {@code status}, {@code createdAt}, and {@code updatedAt} are
 * Catalogue-owned and shown as cached here; the two times are {@code null} when the Catalogue has not
 * sent a readable value. {@code localDescription} and {@code initialized} belong to this authoring tool.
 *
 * <p>The initialization fields ({@code 12} section 3 Project picker, DECISIONS.md D-040) are filled by
 * {@code GET} and {@code PATCH /projects/{id}} and by {@code POST .../initialize}; the organization listings,
 * creation, and restore touch no local initialization state and leave them {@code null} (and
 * {@code enabledSurfaces} empty):
 *
 * @param initializedAt     when initialization succeeded; {@code null} means "resume initialization"
 * @param template          the applied Template version, with {@code name} in the caller's language
 * @param stylePresetKey    the theme's style preset
 * @param applicationStatus {@code DRAFT}, {@code LIVE}, or {@code SUSPENDED} ({@code 03} section 2)
 * @param enabledSurfaces   the enabled surfaces, in order
 */
public record ProjectResponse(UUID id,
                              UUID organizationId,
                              String name,
                              String slug,
                              String status,
                              String localDescription,
                              boolean initialized,
                              Instant createdAt,
                              Instant updatedAt,
                              Instant initializedAt,
                              TemplateSummary template,
                              String stylePresetKey,
                              String applicationStatus,
                              List<String> enabledSurfaces) {

    /** The applied Template version. */
    public record TemplateSummary(UUID id, String key, int version, String name) {

        static TemplateSummary from(AppliedTemplate template) {
            return template == null ? null : new TemplateSummary(template.id(), template.key(), template.version(),
                    LocalizedText.pick(template.name()));
        }
    }

    public static ProjectResponse from(ProjectView view) {
        ProjectInitialization chosen = view.initialization();
        return new ProjectResponse(view.id(), view.organizationId(), view.name(), view.slug(),
                view.status(), view.localDescription(), view.initialized(), view.createdAt(),
                view.updatedAt(), chosen.initializedAt(), TemplateSummary.from(chosen.template()),
                chosen.stylePresetKey(), chosen.applicationStatus(), chosen.enabledSurfaces());
    }
}
