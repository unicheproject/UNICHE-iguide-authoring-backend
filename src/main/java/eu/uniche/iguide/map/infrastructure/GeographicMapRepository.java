package eu.uniche.iguide.map.infrastructure;

import eu.uniche.iguide.map.domain.GeographicMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query takes the Project explicitly. */
public interface GeographicMapRepository extends JpaRepository<GeographicMap, UUID>,
        JpaSpecificationExecutor<GeographicMap> {

    Optional<GeographicMap> findByProjectIdAndId(UUID projectId, UUID id);

    Optional<GeographicMap> findByProjectIdAndPlaceId(UUID projectId, UUID placeId);

    /** Non-archived maps that place the Resource. */
    @Query("""
            select m from GeographicMap m
            where m.projectId = :projectId and m.status <> eu.uniche.iguide.resource.domain.ResourceStatus.ARCHIVED
              and exists (select r.id from GeographicMapResource r
                          where r.projectId = :projectId and r.mapId = m.id
                            and r.resourceType = :resourceType and r.resourceId = :resourceId)
            """)
    List<GeographicMap> findPlacing(@Param("projectId") UUID projectId, @Param("resourceType") String resourceType,
                                    @Param("resourceId") UUID resourceId);
}
