package eu.uniche.iguide.map.infrastructure;

import eu.uniche.iguide.map.domain.GeographicMapResource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query takes the Project explicitly. */
public interface GeographicMapResourceRepository extends JpaRepository<GeographicMapResource, UUID> {

    List<GeographicMapResource> findByProjectIdAndMapIdOrderByIdAsc(UUID projectId, UUID mapId);

    Optional<GeographicMapResource> findByProjectIdAndMapIdAndId(UUID projectId, UUID mapId, UUID id);

    boolean existsByProjectIdAndMapIdAndResourceTypeAndResourceId(UUID projectId, UUID mapId, String resourceType,
                                                                  UUID resourceId);
}
