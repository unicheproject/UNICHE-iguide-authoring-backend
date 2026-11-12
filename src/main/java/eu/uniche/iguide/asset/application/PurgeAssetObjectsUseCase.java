package eu.uniche.iguide.asset.application;

import eu.uniche.iguide.asset.domain.AssetStorageCleanup;
import eu.uniche.iguide.asset.infrastructure.AssetRepository;
import eu.uniche.iguide.asset.infrastructure.AssetStorageCleanupRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Deletes stored objects no Asset points at any more ({@code 04} section 10: binary operations outside the database
 * transaction need a cleanup strategy; DECISIONS.md D-043). A replaced file is queued in the transaction that
 * replaces it and deleted only after signed URLs already handed out for it have expired, so a page that is open keeps
 * showing it until then. An object that an Asset points at again is never deleted; a failed delete is retried on the
 * next run.
 */
@Service
public class PurgeAssetObjectsUseCase {

    /** Longer than any signed URL to the object lives. */
    static final Duration GRACE = AssetRules.URL_VALIDITY.plusMinutes(5);
    private static final int BATCH = 100;
    private static final Logger log = LoggerFactory.getLogger(PurgeAssetObjectsUseCase.class);

    private final AssetStorageCleanupRepository cleanups;
    private final AssetRepository assets;
    private final AssetStorage storage;

    public PurgeAssetObjectsUseCase(AssetStorageCleanupRepository cleanups, AssetRepository assets,
                                    AssetStorage storage) {
        this.cleanups = cleanups;
        this.assets = assets;
        this.storage = storage;
    }

    /** Queues the object for deletion, inside the caller's transaction. */
    public void queue(UUID projectId, String storageKey, Instant now) {
        cleanups.save(AssetStorageCleanup.of(projectId, storageKey, now.plus(GRACE), now));
    }

    @Scheduled(fixedDelayString = "${iguide.assets.cleanup-interval:PT10M}",
            initialDelayString = "${iguide.assets.cleanup-interval:PT10M}")
    public void scheduled() {
        purgeDue(Instant.now());
    }

    /** Deletes every object due by {@code now}; returns how many entries were settled. */
    public int purgeDue(Instant now) {
        int settled = 0;
        while (true) {
            List<AssetStorageCleanup> due = cleanups.findDue(now, PageRequest.of(0, BATCH));
            int failed = 0;
            for (AssetStorageCleanup cleanup : due) {
                if (!assets.existsByStorageKey(cleanup.getStorageKey())) {
                    try {
                        storage.delete(cleanup.getStorageKey());
                    } catch (RuntimeException e) {
                        log.warn("Could not delete the stored object {}; retrying on the next run",
                                cleanup.getStorageKey(), e);
                        failed++;
                        continue;
                    }
                }
                cleanups.delete(cleanup);
                settled++;
            }
            if (due.size() < BATCH || failed == due.size()) {
                return settled;
            }
        }
    }
}
