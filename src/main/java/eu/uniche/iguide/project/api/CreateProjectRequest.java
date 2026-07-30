package eu.uniche.iguide.project.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request to create a Project under an organization.
 *
 * <p>The tool slug is not accepted from the caller. It identifies this backend on the platform and comes
 * from configuration, so a client cannot create a Project that claims to belong to another tool.
 */
public record CreateProjectRequest(
        @NotBlank @Size(max = 200) String name,

        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*",
                message = "must be lowercase alphanumeric segments separated by single hyphens")
        String slug) {
}
