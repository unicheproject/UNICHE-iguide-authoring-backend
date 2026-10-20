package eu.uniche.iguide.trigger.api;

import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.trigger.domain.InteractionTrigger;
import eu.uniche.iguide.trigger.domain.Surface;
import eu.uniche.iguide.trigger.domain.TriggerTargetType;
import eu.uniche.iguide.trigger.domain.TriggerType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** An Interaction Trigger ({@code 03} section 14). */
public record TriggerResponse(UUID id, UUID projectId, TriggerType triggerType, TriggerTargetType targetResourceType,
                              UUID targetResourceId, String code, List<Surface> supportedSurfaces,
                              ResourceStatus status, Map<String, Object> configuration, int sortOrder,
                              Instant archivedAt, Instant createdAt, Instant updatedAt, long version) {

    public static TriggerResponse from(InteractionTrigger t) {
        return new TriggerResponse(t.getId(), t.getProjectId(), t.getTriggerType(), t.getTargetResourceType(),
                t.getTargetResourceId(), t.getCode(), t.getSupportedSurfaces(), t.getStatus(), t.getConfiguration(),
                t.getSortOrder(), t.getArchivedAt(), t.getCreatedAt(), t.getUpdatedAt(), t.getVersion());
    }
}
