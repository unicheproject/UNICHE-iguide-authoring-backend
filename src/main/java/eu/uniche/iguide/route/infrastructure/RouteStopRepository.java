package eu.uniche.iguide.route.infrastructure;

import eu.uniche.iguide.route.domain.RouteStop;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query takes the Project explicitly. */
public interface RouteStopRepository extends JpaRepository<RouteStop, UUID> {

    List<RouteStop> findByProjectIdAndRouteIdOrderByPositionAsc(UUID projectId, UUID routeId);

    List<RouteStop> findByProjectIdAndRouteIdInOrderByPositionAsc(UUID projectId, Collection<UUID> routeIds);

    Optional<RouteStop> findByProjectIdAndRouteIdAndId(UUID projectId, UUID routeId, UUID id);
}
