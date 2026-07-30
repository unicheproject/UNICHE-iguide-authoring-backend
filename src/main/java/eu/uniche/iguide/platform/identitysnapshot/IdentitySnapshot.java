package eu.uniche.iguide.platform.identitysnapshot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A non-authoritative mirror of an authenticated subject, refreshed opportunistically from validated
 * tokens.
 *
 * <p>This is not a User. There is no password, no access flag, no Project membership, no Organization
 * role, and no local account lifecycle that could influence authorization. Deleting a row here
 * removes a display convenience and nothing else.
 *
 * <p>Audit events keep their own actor snapshots, so an audit entry stays readable whether or not the
 * row it describes still exists.
 */
@Entity
@Table(name = "identity_snapshots")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentitySnapshot {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String issuer;

    @Column(nullable = false, updatable = false)
    private String subject;

    private String email;

    @Column(name = "preferred_username")
    private String preferredUsername;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    public static IdentitySnapshot firstSeen(String issuer, String subject, Instant seenAt) {
        IdentitySnapshot snapshot = new IdentitySnapshot();
        snapshot.id = UUID.randomUUID();
        snapshot.issuer = issuer;
        snapshot.subject = subject;
        snapshot.firstSeenAt = seenAt;
        snapshot.lastSeenAt = seenAt;
        return snapshot;
    }

    /** Refreshes the mutable claims and {@code lastSeenAt}. {@code firstSeenAt} is never touched. */
    public void refresh(String email, String preferredUsername, String displayName, Instant seenAt) {
        this.email = email;
        this.preferredUsername = preferredUsername;
        this.displayName = displayName;
        this.lastSeenAt = seenAt;
    }
}
