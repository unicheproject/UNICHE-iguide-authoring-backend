package eu.uniche.iguide.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

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
 * <p>Asset bytes go to an S3 mock container through the production S3 client ({@code 10} section 7: the
 * code path tested is the one that runs in production).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    /** The bucket the test configuration names; created by the mock at startup. */
    protected static final String ASSET_BUCKET = "iguide-assets-test";

    static final GenericContainer<?> S3 = new GenericContainer<>("adobe/s3mock:5.2.3")
            .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", ASSET_BUCKET)
            .withExposedPorts(9090)
            .waitingFor(Wait.forLogMessage(".*Started S3MockApplication.*", 1));

    static {
        POSTGRES.start();
        S3.start();
    }

    @DynamicPropertySource
    static void platformEndpoints(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", TestIdp::issuer);
        registry.add("iguide.assets.s3.endpoint", PostgresIntegrationTest::s3Endpoint);
    }

    protected static String s3Endpoint() {
        return "http://" + S3.getHost() + ":" + S3.getMappedPort(9090);
    }
}
