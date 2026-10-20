package eu.uniche.iguide.guide.infrastructure;

import eu.uniche.iguide.guide.domain.GuideBlock;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query takes the Project explicitly. */
public interface GuideBlockRepository extends JpaRepository<GuideBlock, UUID> {

    List<GuideBlock> findByProjectIdAndGuideIdOrderBySortOrderAsc(UUID projectId, UUID guideId);

    List<GuideBlock> findByProjectIdAndGuideIdInOrderBySortOrderAsc(UUID projectId, Collection<UUID> guideIds);

    Optional<GuideBlock> findByProjectIdAndGuideIdAndId(UUID projectId, UUID guideId, UUID id);
}
