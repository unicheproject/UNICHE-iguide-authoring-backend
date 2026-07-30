package eu.uniche.iguide.platform.identitysnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import eu.uniche.iguide.platform.security.UnichePrincipal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * The concurrent-first-request path, made deterministic.
 *
 * <p>Two requests for the same new subject can both miss the row and both attempt an insert. The loser
 * must recover by re-reading the winner's row, which is only possible because the upsert is not
 * transactional: inside a transaction the failed flush would leave the session unusable for the retry.
 */
class IdentityProvisioningServiceRaceTest {

    private static final UnichePrincipal PRINCIPAL = new UnichePrincipal(
            "https://idp.test.local/realms/uniche", "subject-1", "someone@example.org", "someone",
            "Someone", "token");

    @Test
    void losingTheInsertRaceUpdatesTheWinnersRow() {
        IdentitySnapshotRepository repository = mock(IdentitySnapshotRepository.class);
        IdentitySnapshot winner = IdentitySnapshot.firstSeen(
                PRINCIPAL.issuer(), PRINCIPAL.subject(), Instant.parse("2026-01-01T00:00:00Z"));

        when(repository.findByIssuerAndSubject(PRINCIPAL.issuer(), PRINCIPAL.subject()))
                .thenReturn(Optional.empty())     // the initial miss
                .thenReturn(Optional.of(winner)); // the re-read after the violation
        when(repository.save(any(IdentitySnapshot.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate (issuer, subject)"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IdentitySnapshot result = new IdentityProvisioningService(repository).record(PRINCIPAL);

        assertThat(result).isSameAs(winner);
        assertThat(result.getEmail()).isEqualTo("someone@example.org");
        assertThat(result.getFirstSeenAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(result.getLastSeenAt()).isAfter(result.getFirstSeenAt());
    }

    /**
     * A violation with no row behind it is a different fault. Swallowing it behind another insert attempt
     * would hide a genuine constraint problem.
     */
    @Test
    void aViolationWithNoWinningRowIsNotSwallowed() {
        IdentitySnapshotRepository repository = mock(IdentitySnapshotRepository.class);
        when(repository.findByIssuerAndSubject(PRINCIPAL.issuer(), PRINCIPAL.subject()))
                .thenReturn(Optional.empty());
        when(repository.save(any(IdentitySnapshot.class)))
                .thenThrow(new DataIntegrityViolationException("some other constraint"));

        assertThatThrownBy(() -> new IdentityProvisioningService(repository).record(PRINCIPAL))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("some other constraint");
    }
}
