package eu.uniche.iguide.platform.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A cached snapshot of a Catalogue organization, kept for display names, project pickers, reporting,
 * audit context, and diagnostics.
 *
 * <p>This is not an Organization. There are no membership lists, no authorization flags, no manager
 * relationships, and no invitation model here, and there never will be: the Catalogue owns all of
 * that.
 */
@Entity
@Table(name = "catalogue_organization_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogueOrganizationSnapshot {

    /** The Catalogue organization UUID, used directly as the primary key. */
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String slug;

    private String status;

    @Column(name = "catalogue_created_at")
    private Instant catalogueCreatedAt;

    @Column(name = "catalogue_updated_at")
    private Instant catalogueUpdatedAt;

    @Column(name = "last_synchronized_at", nullable = false)
    private Instant lastSynchronizedAt;

    public static CatalogueOrganizationSnapshot of(UUID id, String name, String slug, String status,
                                                   Instant synchronizedAt) {
        CatalogueOrganizationSnapshot snapshot = new CatalogueOrganizationSnapshot();
        snapshot.id = id;
        snapshot.apply(name, slug, status, synchronizedAt);
        return snapshot;
    }

    public void apply(String name, String slug, String status, Instant synchronizedAt) {
        this.name = name;
        this.slug = slug;
        this.status = status;
        this.lastSynchronizedAt = synchronizedAt;
    }
}
