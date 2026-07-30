package eu.uniche.iguide.project.api;

import eu.uniche.iguide.project.application.ProjectView;
import java.util.UUID;

/**
 * This API's representation of a Project.
 *
 * <p>{@code name}, {@code slug}, and {@code status} are Catalogue-owned and shown as cached here.
 * {@code localDescription} and {@code initialized} belong to this authoring tool.
 */
public record ProjectResponse(UUID id,
                              UUID organizationId,
                              String name,
                              String slug,
                              String status,
                              String localDescription,
                              boolean initialized) {

    public static ProjectResponse from(ProjectView view) {
        return new ProjectResponse(view.id(), view.organizationId(), view.name(), view.slug(),
                view.status(), view.localDescription(), view.initialized());
    }
}
