package eu.uniche.iguide.surface.infrastructure;

import eu.uniche.iguide.surface.domain.ProjectSurface;
import eu.uniche.iguide.surface.domain.ProjectSurfaceId;
import eu.uniche.iguide.surface.domain.Surface;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query takes the Project explicitly ({@code 06} section 2). */
public interface ProjectSurfaceRepository extends JpaRepository<ProjectSurface, ProjectSurfaceId> {

    List<ProjectSurface> findByProjectIdOrderBySortOrderAsc(UUID projectId);

    Optional<ProjectSurface> findByProjectIdAndSurface(UUID projectId, Surface surface);
}
