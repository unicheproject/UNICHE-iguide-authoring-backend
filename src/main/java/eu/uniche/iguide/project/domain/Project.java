package eu.uniche.iguide.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The local companion row for a Catalogue-owned Project.
 *
 * <p>The Catalogue Project UUID is used directly as this primary key. There is no mapping table and no
 * locally generated identifier, so the same UUID identifies the Project in the Catalogue, in every
 * authored table's {@code project_id}, and in every API path.
 *
 * <p>This row is never proof of access. Its presence says the Catalogue once acknowledged the Project,
 * nothing more; every Project-scoped request re-checks access upstream.
 *
 * <p>{@code catalogueName}, {@code catalogueSlug}, and {@code catalogueStatus} are cached snapshots the
 * Catalogue owns. {@code localDescription} and {@code initializedAt} are owned by this authoring tool.
 */
@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "catalogue_name", nullable = false)
    private String catalogueName;

    @Column(name = "catalogue_slug")
    private String catalogueSlug;

    @Column(name = "catalogue_status")
    private String catalogueStatus;

    @Column(name = "catalogue_created_at")
    private Instant catalogueCreatedAt;

    @Column(name = "catalogue_updated_at")
    private Instant catalogueUpdatedAt;

    @Column(name = "last_synchronized_at", nullable = false)
    private Instant lastSynchronizedAt;

    @Setter
    @Column(name = "local_description")
    private String localDescription;

    @Column(name = "initialized_at")
    private Instant initializedAt;

    /**
     * Set when the Catalogue answered {@code 404} for this Project on a read. Nothing else sets it: an
     * unreachable Catalogue is not evidence that a Project is gone.
     */
    @Column(name = "locally_deleted_at")
    private Instant locallyDeletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static Project companionOf(UUID id, UUID organizationId, String name, String slug,
                                      String status, Instant now) {
        Project project = new Project();
        project.id = id;
        project.createdAt = now;
        project.applyCatalogueState(organizationId, name, slug, status, now);
        return project;
    }

    /** Refreshes the Catalogue-owned fields. Locally owned fields are untouched. */
    public void applyCatalogueState(UUID organizationId, String name, String slug, String status,
                                   Instant now) {
        this.organizationId = organizationId;
        this.catalogueName = name;
        this.catalogueSlug = slug;
        this.catalogueStatus = status;
        this.lastSynchronizedAt = now;
        this.updatedAt = now;
        // A Project the Catalogue is answering for again is not deleted, whatever an earlier 404 said.
        this.locallyDeletedAt = null;
    }

    public void markLocallyDeleted(Instant now) {
        this.locallyDeletedAt = now;
        this.updatedAt = now;
    }

    public void markInitialized(Instant now) {
        this.initializedAt = now;
        this.updatedAt = now;
    }

    public boolean isLocallyDeleted() {
        return locallyDeletedAt != null;
    }
}
