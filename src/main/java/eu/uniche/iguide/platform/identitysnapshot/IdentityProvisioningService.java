package eu.uniche.iguide.platform.identitysnapshot;

import eu.uniche.iguide.platform.security.UnichePrincipal;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Keeps the local identity mirror current.
 *
 * <p>There is no registration step on the platform and no login hook here, because login happens
 * entirely in Keycloak. The only correct moment to refresh a snapshot is therefore every request,
 * not a one-off account creation event.
 */
@Service
public class IdentityProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(IdentityProvisioningService.class);

    private final IdentitySnapshotRepository repository;

    public IdentityProvisioningService(IdentitySnapshotRepository repository) {
        this.repository = repository;
    }

    /**
     * Idempotently records the subject. The claims and {@code lastSeenAt} are refreshed on every
     * call; {@code firstSeenAt} is written only on creation.
     *
     * <p>Deliberately <strong>not</strong> {@code @Transactional}. Two concurrent first requests for
     * the same new subject can both miss the row and both attempt an insert; the loser catches the
     * unique constraint violation, re-reads the winner's row, and updates that instead. That fallback
     * only works outside a transaction, because a failed flush inside one leaves the persistence
     * session unusable for any subsequent write, so the retry would fail too.
     */
    public IdentitySnapshot record(UnichePrincipal principal) {
        Instant now = Instant.now();
        return repository.findByIssuerAndSubject(principal.issuer(), principal.subject())
                .map(existing -> refresh(existing, principal, now))
                .orElseGet(() -> create(principal, now));
    }

    private IdentitySnapshot create(UnichePrincipal principal, Instant now) {
        IdentitySnapshot snapshot =
                IdentitySnapshot.firstSeen(principal.issuer(), principal.subject(), now);
        snapshot.refresh(principal.email(), principal.preferredUsername(), principal.displayName(), now);
        try {
            return repository.save(snapshot);
        } catch (DataIntegrityViolationException lostTheRace) {
            log.debug("Concurrent first request for subject {} won the insert; updating its row",
                    principal.subject());
            IdentitySnapshot winner = repository
                    .findByIssuerAndSubject(principal.issuer(), principal.subject())
                    // If the row is genuinely absent the violation was something else, and hiding it
                    // behind a second insert attempt would only obscure the real fault.
                    .orElseThrow(() -> lostTheRace);
            return refresh(winner, principal, now);
        }
    }

    private IdentitySnapshot refresh(IdentitySnapshot snapshot, UnichePrincipal principal, Instant now) {
        snapshot.refresh(principal.email(), principal.preferredUsername(), principal.displayName(), now);
        return repository.save(snapshot);
    }
}
