package eu.uniche.iguide.platform;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class PlatformConfigurationCheckTest {

    private final PlatformConfigurationCheck check = new PlatformConfigurationCheck();

    @Test
    void refusesAnEnvironmentWithNoIssuer() {
        assertThatThrownBy(() -> check.postProcessEnvironment(
                        environmentWith(Map.of("uniche.catalogue.base-url", "https://platform.example")),
                        null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IDP_ISSUER_URI");
    }

    @Test
    void refusesAnEnvironmentWithNoCatalogueUrl() {
        assertThatThrownBy(() -> check.postProcessEnvironment(
                        environmentWith(Map.of(
                                "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                                "https://idp.example/realms/uniche")),
                        null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CATALOGUE_BASE_URL");
    }

    @Test
    void refusesABlankValue() {
        assertThatThrownBy(() -> check.postProcessEnvironment(
                        environmentWith(Map.of(
                                "spring.security.oauth2.resourceserver.jwt.issuer-uri", "  ",
                                "uniche.catalogue.base-url", "https://platform.example")),
                        null))
                .isInstanceOf(IllegalStateException.class);
    }

    /** An unset environment variable behind a placeholder is the same situation as an absent property. */
    @Test
    void refusesAnUnresolvablePlaceholder() {
        assertThatThrownBy(() -> check.postProcessEnvironment(
                        environmentWith(Map.of(
                                "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                                "${SOME_VARIABLE_NOBODY_SET}",
                                "uniche.catalogue.base-url", "https://platform.example")),
                        null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsAFullyConfiguredEnvironment() {
        assertThatCode(() -> check.postProcessEnvironment(
                        environmentWith(Map.of(
                                "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                                "https://idp.example/realms/uniche",
                                "uniche.catalogue.base-url", "https://platform.example")),
                        null))
                .doesNotThrowAnyException();
    }

    private static StandardEnvironment environmentWith(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources()
                .addFirst(new MapPropertySource("test", properties));
        return environment;
    }
}
