package eu.uniche.iguide.asset.infrastructure;

import eu.uniche.iguide.asset.domain.AssetStorageCleanup;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * The cleanup job is the one reader across Projects: it deletes objects, never reads Project data
 * (DECISIONS.md D-043).
 */
public interface AssetStorageCleanupRepository extends JpaRepository<AssetStorageCleanup, UUID> {

    @Query("select c from AssetStorageCleanup c where c.deleteAfter <= :now order by c.deleteAfter")
    List<AssetStorageCleanup> findDue(Instant now, Pageable page);
}
