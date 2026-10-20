package eu.uniche.iguide.trigger.api;

import eu.uniche.iguide.trigger.domain.Surface;
import eu.uniche.iguide.trigger.domain.TriggerTargetType;
import eu.uniche.iguide.trigger.domain.TriggerType;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request bodies of the trigger endpoints. */
public final class TriggerRequests {

    private TriggerRequests() {
    }

    /** {@code code} only for {@code QR_CODE}, generated when absent; {@code supportedSurfaces} null for every surface. */
    public record Create(TriggerTargetType targetResourceType, UUID targetResourceId, TriggerType triggerType,
                         String code, List<Surface> supportedSurfaces, Map<String, Object> configuration) {
    }

    /** The type and the target never change. */
    public record Update(@NotNull Long version, String code, List<Surface> supportedSurfaces,
                         Map<String, Object> configuration) {
    }
}
