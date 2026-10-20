package eu.uniche.iguide.guide.infrastructure;

import eu.uniche.iguide.guide.domain.GuideBlockTranslation;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query takes the Project explicitly. */
public interface GuideBlockTranslationRepository extends JpaRepository<GuideBlockTranslation, UUID> {

    List<GuideBlockTranslation> findByProjectIdAndGuideBlockIdIn(UUID projectId, Collection<UUID> guideBlockIds);
}
