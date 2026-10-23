package eu.uniche.iguide.surface.application;

import eu.uniche.iguide.surface.domain.Surface;
import java.util.Map;

public record SurfaceView(Surface surface, boolean enabled, int sortOrder, Map<String, Object> configuration) {
}
