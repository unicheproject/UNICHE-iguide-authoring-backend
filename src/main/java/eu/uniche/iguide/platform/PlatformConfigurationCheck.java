package eu.uniche.iguide.platform;

import java.util.List;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * Refuses to start without the two platform coordinates that have no default anywhere: the Keycloak
 * issuer and the Catalogue base URL.
 *
 * <p>This runs before any bean exists, which is the point. Validating them through
 * {@code @ConfigurationProperties} alone is not enough: the datasource and Liquibase are initialized
 * earlier, so a deployment missing its issuer would fail with a database message instead, and one whose
 * database happened to be reachable would start and only misbehave later, against whichever platform
 * instance it guessed.
 *
 * <p>A misconfigured deployment must look broken immediately rather than look healthy while reading or
 * writing the wrong platform.
 *
 * <p>The same holds for the AI gateway mode (DECISIONS.md D-035): an unknown value is refused, and so is the
 * {@code mock} gateway under the {@code prod} profile, where its output would reach real content (AGENTS.md rule 9).
 */
public class PlatformConfigurationCheck implements EnvironmentPostProcessor {

    private static final List<RequiredProperty> REQUIRED = List.of(
            new RequiredProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                    "IDP_ISSUER_URI", "the Keycloak realm this backend trusts"),
            new RequiredProperty("uniche.catalogue.base-url",
                    "CATALOGUE_BASE_URL", "the UNICHE Catalogue instance that authorizes access"));

    static final String AI_GATEWAY_MODE = "iguide.ai.gateway.mode";

    private static final Set<String> AI_GATEWAY_MODES = Set.of("disabled", "mock");

    private record RequiredProperty(String property, String environmentVariable, String describes) {
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        for (RequiredProperty required : REQUIRED) {
            if (isBlank(valueOf(environment, required.property()))) {
                throw new IllegalStateException(
                        "%s is not configured. Set the %s environment variable to %s. It has no default: "
                        .formatted(required.property(), required.environmentVariable(),
                                required.describes())
                        + "a default here would let a misconfigured deployment start against the wrong "
                        + "platform instance.");
            }
        }
        checkAiGatewayMode(environment);
    }

    private static void checkAiGatewayMode(ConfigurableEnvironment environment) {
        String mode = valueOf(environment, AI_GATEWAY_MODE);
        if (isBlank(mode)) {
            return;
        }
        if (!AI_GATEWAY_MODES.contains(mode)) {
            throw new IllegalStateException("%s=%s is not a known mode. Set IGUIDE_AI_GATEWAY_MODE to disabled or mock."
                    .formatted(AI_GATEWAY_MODE, mode));
        }
        if ("mock".equals(mode) && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("%s=mock is refused under the prod profile: the mock gateway's output "
                    .formatted(AI_GATEWAY_MODE) + "is not a translation and must never reach real content.");
        }
    }

    /**
     * An unresolvable placeholder is the same situation as an absent property: the environment variable
     * behind it was never set.
     */
    private static String valueOf(ConfigurableEnvironment environment, String property) {
        try {
            return environment.getProperty(property);
        } catch (RuntimeException unresolvable) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
