package eu.uniche.iguide.surface.application;

import eu.uniche.iguide.surface.domain.Surface;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The surfaces the Template applied to a Project supports ({@code 03} section 2, {@code 04} section 3). Implemented
 * by the template package, so this package does not depend on it.
 */
public interface SurfaceSupport {

    /** Empty while the Project is not initialized: no Template restricts its surfaces yet (DECISIONS.md D-040). */
    Optional<Set<Surface>> supportedSurfaces(UUID projectId);
}
