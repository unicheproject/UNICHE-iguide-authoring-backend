package eu.uniche.iguide.asset.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A stored object no Asset points at any more, such as the old file of a replaced Asset, waiting until signed URLs
 * already handed out for it have expired (DECISIONS.md D-043).
 */
@Entity
@Table(name = "asset_storage_cleanups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetStorageCleanup {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "storage_key", nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "delete_after", nullable = false, updatable = false)
    private Instant deleteAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AssetStorageCleanup of(UUID projectId, String storageKey, Instant deleteAfter, Instant now) {
        AssetStorageCleanup cleanup = new AssetStorageCleanup();
        cleanup.projectId = projectId;
        cleanup.storageKey = storageKey;
        cleanup.deleteAfter = deleteAfter;
        cleanup.createdAt = now;
        return cleanup;
    }
}
