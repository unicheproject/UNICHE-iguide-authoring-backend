package eu.uniche.iguide.floorplan.infrastructure;

import eu.uniche.iguide.floorplan.domain.FloorPlanPosition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query takes the Project explicitly. */
public interface FloorPlanPositionRepository extends JpaRepository<FloorPlanPosition, UUID> {

    List<FloorPlanPosition> findByProjectIdAndFloorPlanIdOrderByIdAsc(UUID projectId, UUID floorPlanId);

    Optional<FloorPlanPosition> findByProjectIdAndFloorPlanIdAndId(UUID projectId, UUID floorPlanId, UUID id);

    boolean existsByProjectIdAndFloorPlanIdAndResourceTypeAndResourceId(UUID projectId, UUID floorPlanId,
                                                                        String resourceType, UUID resourceId);
}
