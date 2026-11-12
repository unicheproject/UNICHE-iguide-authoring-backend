package eu.uniche.iguide.template.application;

import eu.uniche.iguide.surface.application.SurfaceSupport;
import eu.uniche.iguide.surface.domain.Surface;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The applied Template's {@code supportedSurfaces} ({@code 03} section 2, {@code 04} section 11). */
@Component
public class TemplateSurfaceSupport implements SurfaceSupport {

    private final TemplateLookup lookup;

    public TemplateSurfaceSupport(TemplateLookup lookup) {
        this.lookup = lookup;
    }

    @Override
    public Optional<Set<Surface>> supportedSurfaces(UUID projectId) {
        return lookup.applied(projectId).map(applied -> EnumSet.copyOf(applied.definition().supportedSurfaces()));
    }
}
