package eu.uniche.iguide.support;

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
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void platformEndpoints(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", TestIdp::issuer);
    }
}
