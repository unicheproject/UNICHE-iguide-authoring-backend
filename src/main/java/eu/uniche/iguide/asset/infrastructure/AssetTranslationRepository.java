package eu.uniche.iguide.asset.infrastructure;

import eu.uniche.iguide.asset.domain.AssetTranslation;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query takes the Project explicitly. */
public interface AssetTranslationRepository extends JpaRepository<AssetTranslation, UUID> {

    List<AssetTranslation> findByProjectIdAndAssetId(UUID projectId, UUID assetId);

    List<AssetTranslation> findByProjectIdAndAssetIdIn(UUID projectId, Collection<UUID> assetIds);
}
