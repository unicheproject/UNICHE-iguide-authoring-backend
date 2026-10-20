package eu.uniche.iguide.trigger.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * How a trigger activates content ({@code 03} section 14), with the surfaces able to run it ({@code 13}
 * section 1). {@code MANUAL} needs no row and {@code CAMERA_RECOGNITION} is deferred ({@code 08}), so
 * neither is accepted yet (DECISIONS.md D-026).
 */
public enum TriggerType {
    MANUAL(EnumSet.allOf(Surface.class), false),
    QR_CODE(EnumSet.allOf(Surface.class), true),
    GEO_PROXIMITY(EnumSet.of(Surface.WEB, Surface.MOBILE), true),
    BEACON(EnumSet.of(Surface.MOBILE), true),
    CAMERA_RECOGNITION(EnumSet.noneOf(Surface.class), false);

    private final Set<Surface> surfaces;
    private final boolean authorable;

    TriggerType(Set<Surface> surfaces, boolean authorable) {
        this.surfaces = surfaces;
        this.authorable = authorable;
    }

    public boolean runsOn(Surface surface) {
        return surfaces.contains(surface);
    }

    /** Whether authors can create triggers of this type now. */
    public boolean isAuthorable() {
        return authorable;
    }
}
