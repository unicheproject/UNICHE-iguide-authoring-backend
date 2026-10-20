package eu.uniche.iguide.route.infrastructure;

import eu.uniche.iguide.route.domain.RouteStopTranslation;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query takes the Project explicitly. */
public interface RouteStopTranslationRepository extends JpaRepository<RouteStopTranslation, UUID> {

    List<RouteStopTranslation> findByProjectIdAndRouteStopIdIn(UUID projectId, Collection<UUID> routeStopIds);
}
