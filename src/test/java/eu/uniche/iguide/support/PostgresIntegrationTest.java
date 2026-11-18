package eu.uniche.iguide.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for every test that touches persistence.
 *
 * <p>Liquibase creates the schema and {@code ddl-auto=validate} then compares the JPA mappings against
 * it, so drift between an entity and its changeset fails the build here rather than in production.
 *
 * <p>The container is started once for the whole build and left to the JVM to reap, rather than being
 * managed per test class: the schema comes from migrations that are identical every time, so there is
 * nothing to isolate by restarting it.
 *
 * <p>The issuer points at {@link TestIdp}, so the production security configuration is used unmodified —
 * real discovery, real decoder, real validators.
 *
 * <p>Asset files go to a temporary directory through the production local-disk storage.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    /** Where this build's asset files go; one directory for the whole JVM, like the container. */
    protected static final Path ASSET_DIR = createAssetDir();

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void platformEndpoints(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", TestIdp::issuer);
        registry.add("iguide.assets.dir", ASSET_DIR::toString);
    }

    private static Path createAssetDir() {
        try {
            Path dir = Files.createTempDirectory("iguide-assets-test");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
