package eu.uniche.iguide.poi.infrastructure;

import eu.uniche.iguide.poi.domain.PoiTranslation;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query takes the Project explicitly. */
public interface PoiTranslationRepository extends JpaRepository<PoiTranslation, UUID> {

    List<PoiTranslation> findByProjectIdAndPoiId(UUID projectId, UUID poiId);

    List<PoiTranslation> findByProjectIdAndPoiIdIn(UUID projectId, Collection<UUID> poiIds);

    @Query("""
            select count(t) from PoiTranslation t, PointOfInterest p
            where t.projectId = :projectId and p.projectId = :projectId and p.id = t.poiId
              and t.languageCode = :languageCode and p.status <> :excluded
              and t.title is not null and trim(t.title) <> ''
            """)
    long countTitled(@Param("projectId") UUID projectId, @Param("languageCode") String languageCode,
                     @Param("excluded") ResourceStatus excluded);
}
