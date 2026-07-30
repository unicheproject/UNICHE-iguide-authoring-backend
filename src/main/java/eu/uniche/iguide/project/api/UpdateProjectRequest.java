package eu.uniche.iguide.project.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to update a Project.
 *
 * <p>{@code name} goes to the Catalogue, which owns it. {@code localDescription} stays here. It is
 * nullable, and null means "leave it unchanged": clearing it requires sending an empty string.
 *
 * <p>The slug is absent on purpose. Changing it would break every reference the platform has already
 * handed out, so if it is ever allowed it will be its own operation.
 */
public record UpdateProjectRequest(@NotBlank @Size(max = 200) String name,
                                   @Size(max = 2000) String localDescription) {
}
