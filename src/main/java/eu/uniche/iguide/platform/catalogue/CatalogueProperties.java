package eu.uniche.iguide.platform.catalogue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Catalogue connection settings.
 *
 * <p>{@code baseUrl} has no default anywhere. An unset value must stop the application at startup
 * rather than let it come up and later read or write the wrong platform instance, so it is validated
 * here instead of being discovered on the first call.
 */
@Validated
@ConfigurationProperties(prefix = "uniche.catalogue")
public record CatalogueProperties(@NotBlank String baseUrl,
                                  @NotNull Duration connectTimeout,
                                  @NotNull Duration readTimeout) {
}
