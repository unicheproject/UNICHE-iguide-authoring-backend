package eu.uniche.iguide.platform.identitysnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.support.PostgresIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IdentityProvisioningIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private IdentityProvisioningService provisioningService;

    @Autowired
    private IdentitySnapshotRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void recordsASubjectOnFirstSight() {
        IdentitySnapshot snapshot = provisioningService.record(principal("subject-1", "someone"));

        assertThat(snapshot.getSubject()).isEqualTo("subject-1");
        assertThat(snapshot.getEmail()).isEqualTo("someone@example.org");
        assertThat(snapshot.getFirstSeenAt()).isNotNull();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void refreshesClaimsAndLastSeenButNeverFirstSeen() throws Exception {
        IdentitySnapshot first = provisioningService.record(principal("subject-1", "old-name"));
        Instant firstSeenAt = first.getFirstSeenAt();
        Thread.sleep(10);

        IdentitySnapshot second = provisioningService.record(principal("subject-1", "new-name"));

        assertThat(repository.count()).isEqualTo(1);
        // Compared with a microsecond tolerance because that is the resolution `timestamptz` stores: the
        // value read back is the JVM's instant rounded, not truncated. What the assertion is about is
        // that the field was not rewritten, and a one-microsecond window says exactly that.
        assertThat(second.getFirstSeenAt())
                .isCloseTo(firstSeenAt, within(1, ChronoUnit.MICROS));
        assertThat(second.getLastSeenAt()).isAfter(firstSeenAt);
        assertThat(second.getDisplayName()).isEqualTo("new-name");
    }

    /**
     * The real race, against a real unique constraint. Every request must succeed and exactly one row must
     * exist: there is no login event to serialize on, so this happens whenever a new person's client fires
     * several requests at once.
     */
    @Test
    void concurrentFirstRequestsForOneNewSubjectAllSucceed() throws Exception {
        int concurrency = 8;
        UnichePrincipal principal = principal(UUID.randomUUID().toString(), "someone");

        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency)) {
            List<Callable<IdentitySnapshot>> calls = IntStream.range(0, concurrency)
                    .<Callable<IdentitySnapshot>>mapToObj(
                            index -> () -> provisioningService.record(principal))
                    .toList();

            for (Future<IdentitySnapshot> outcome : executor.invokeAll(calls)) {
                // get() rethrows anything a worker threw, so a swallowed race surfaces here.
                assertThat(outcome.get()).isNotNull();
            }
        }

        assertThat(repository.count()).isEqualTo(1);
    }

    private static UnichePrincipal principal(String subject, String displayName) {
        return new UnichePrincipal("https://idp.test.local/realms/uniche", subject,
                "someone@example.org", "someone", displayName, "token");
    }
}
