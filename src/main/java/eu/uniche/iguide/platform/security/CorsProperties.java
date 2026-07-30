package eu.uniche.iguide.platform.security;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Browser origins allowed to call this backend. An environment axis with a local-development
 * default; misconfiguration surfaces in the browser rather than at startup, so the list is only
 * required to be non-empty.
 */
@Validated
@ConfigurationProperties(prefix = "iguide.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {
}
