package eu.uniche.iguide.surface.application;

import eu.uniche.iguide.surface.domain.Surface;
import java.util.Map;

/** One requested surface: whether it is enabled, and its configuration. */
public record SurfaceInput(Surface surface, boolean enabled, Map<String, Object> configuration) {
}
