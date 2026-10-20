package eu.uniche.iguide.guide.infrastructure;

import eu.uniche.iguide.guide.domain.GuideTranslation;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query takes the Project explicitly. */
public interface GuideTranslationRepository extends JpaRepository<GuideTranslation, UUID> {

    List<GuideTranslation> findByProjectIdAndGuideId(UUID projectId, UUID guideId);

    List<GuideTranslation> findByProjectIdAndGuideIdIn(UUID projectId, Collection<UUID> guideIds);

    @Query("""
            select count(t) from GuideTranslation t, Guide g
            where t.projectId = :projectId and g.projectId = :projectId and g.id = t.guideId
              and t.languageCode = :languageCode and g.status <> :excluded
              and t.title is not null and trim(t.title) <> ''
            """)
    long countTitled(@Param("projectId") UUID projectId, @Param("languageCode") String languageCode,
                     @Param("excluded") ResourceStatus excluded);
}
