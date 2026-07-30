package eu.uniche.iguide.platform.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The Catalogue URL has no default anywhere, so an unset value must stop the application at startup
 * rather than surface on the first call that needs it.
 */
class CataloguePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(EnableCatalogueProperties.class);

    @Test
    void failsToStartWhenTheCatalogueUrlIsMissing() {
        runner.withPropertyValues(
                        "uniche.catalogue.connect-timeout=5s",
                        "uniche.catalogue.read-timeout=10s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsToStartWhenTheCatalogueUrlIsBlank() {
        runner.withPropertyValues(
                        "uniche.catalogue.base-url=",
                        "uniche.catalogue.connect-timeout=5s",
                        "uniche.catalogue.read-timeout=10s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void bindsTimeoutsAsDurations() {
        runner.withPropertyValues(
                        "uniche.catalogue.base-url=https://platform.example.org",
                        "uniche.catalogue.connect-timeout=5s",
                        "uniche.catalogue.read-timeout=10s")
                .run(context -> {
                    CatalogueProperties properties = context.getBean(CatalogueProperties.class);
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
                });
    }

    @EnableConfigurationProperties(CatalogueProperties.class)
    static class EnableCatalogueProperties {
    }
}
