package eu.uniche.iguide.place.infrastructure;

import eu.uniche.iguide.place.domain.PlaceTranslation;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query takes the Project explicitly. */
public interface PlaceTranslationRepository extends JpaRepository<PlaceTranslation, UUID> {

    List<PlaceTranslation> findByProjectIdAndPlaceId(UUID projectId, UUID placeId);

    List<PlaceTranslation> findByProjectIdAndPlaceIdIn(UUID projectId, Collection<UUID> placeIds);

    @Query("""
            select count(t) from PlaceTranslation t, Place p
            where t.projectId = :projectId and p.projectId = :projectId and p.id = t.placeId
              and t.languageCode = :languageCode and p.status <> :excluded
              and t.title is not null and trim(t.title) <> ''
            """)
    long countTitled(@Param("projectId") UUID projectId, @Param("languageCode") String languageCode,
                     @Param("excluded") ResourceStatus excluded);
}
