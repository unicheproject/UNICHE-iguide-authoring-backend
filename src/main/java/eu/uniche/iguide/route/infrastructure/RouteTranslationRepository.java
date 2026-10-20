package eu.uniche.iguide.route.infrastructure;

import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.route.domain.RouteTranslation;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query takes the Project explicitly. */
public interface RouteTranslationRepository extends JpaRepository<RouteTranslation, UUID> {

    List<RouteTranslation> findByProjectIdAndRouteId(UUID projectId, UUID routeId);

    List<RouteTranslation> findByProjectIdAndRouteIdIn(UUID projectId, Collection<UUID> routeIds);

    @Query("""
            select count(t) from RouteTranslation t, Route r
            where t.projectId = :projectId and r.projectId = :projectId and r.id = t.routeId
              and t.languageCode = :languageCode and r.status <> :excluded
              and t.title is not null and trim(t.title) <> ''
            """)
    long countTitled(@Param("projectId") UUID projectId, @Param("languageCode") String languageCode,
                     @Param("excluded") ResourceStatus excluded);
}
