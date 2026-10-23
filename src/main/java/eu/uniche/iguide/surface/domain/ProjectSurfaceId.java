package eu.uniche.iguide.surface.domain;

import java.io.Serializable;
import java.util.UUID;

/** Composite key of {@link ProjectSurface}: a surface exists once per Project. */
public record ProjectSurfaceId(UUID projectId, Surface surface) implements Serializable {

    /** For JPA, which instantiates id classes reflectively. */
    public ProjectSurfaceId() {
        this(null, null);
    }
}
