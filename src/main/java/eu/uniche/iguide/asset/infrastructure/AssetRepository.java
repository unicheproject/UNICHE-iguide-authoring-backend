package eu.uniche.iguide.asset.infrastructure;

import eu.uniche.iguide.asset.domain.Asset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Every query takes the Project explicitly ({@code 06} section 2). */
public interface AssetRepository extends JpaRepository<Asset, UUID>, JpaSpecificationExecutor<Asset> {

    Optional<Asset> findByProjectIdAndId(UUID projectId, UUID id);

    List<Asset> findByProjectIdAndIdIn(UUID projectId, Collection<UUID> ids);
}
