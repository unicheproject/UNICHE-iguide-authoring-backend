package eu.uniche.iguide.platform.catalogue;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Identity of this authoring tool on the platform, as registered in the Catalogue. Sent as
 * {@code toolSlug} when a Project is created, which is how the Catalogue knows which tool owns it.
 */
@Validated
@ConfigurationProperties(prefix = "uniche.tool")
public record ToolProperties(@NotBlank String slug) {
}
