package eu.uniche.iguide.workingcopy.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The handler of each kind, and the conversion between a stored payload and the kind's payload record. */
@Component
public class WorkingCopyHandlers {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final Map<WorkingCopyResourceType, WorkingCopyHandler<?>> byType =
            new EnumMap<>(WorkingCopyResourceType.class);
    private final ObjectMapper objectMapper;

    public WorkingCopyHandlers(List<WorkingCopyHandler<?>> handlers, ObjectMapper objectMapper) {
        handlers.forEach(handler -> byType.put(handler.type(), handler));
        this.objectMapper = objectMapper;
    }

    /** The collection segment of a path, such as {@code floor-plans}; an unknown one is a {@code 404}. */
    public WorkingCopyResourceType typeOf(String pathSegment) {
        return WorkingCopyResourceType.fromPathSegment(pathSegment)
                .filter(byType::containsKey)
                .orElseThrow(() -> new ResourceNotFoundException("No such resource kind has working copies."));
    }

    @SuppressWarnings("unchecked")
    public <S> WorkingCopyHandler<S> handler(WorkingCopyResourceType type) {
        return (WorkingCopyHandler<S>) byType.get(type);
    }

    /** The payload as the kind's record; a payload of the wrong shape is a {@code 422} at {@code payload}. */
    public <S> S read(WorkingCopyHandler<S> handler, Map<String, Object> payload) {
        try {
            return objectMapper.convertValue(payload == null ? Map.of() : payload, handler.stateType());
        } catch (IllegalArgumentException e) {
            throw new ValidationFailedException("The pending changes are not in the expected shape.",
                    List.of(new ErrorItem("payload", "INVALID_FORMAT",
                            "Does not match the fields of a " + handler.type().label() + ".")));
        }
    }

    /**
     * The Resource's current rows as a payload, inside the caller's transaction: its read DTO, whose field names
     * match the payload records, without the INLINE media that follow the rich text (DECISIONS.md D-031).
     */
    public Map<String, Object> liveState(WorkingCopyHandler<?> handler, UUID projectId, UUID resourceId) {
        Map<String, Object> rendered = new HashMap<>(objectMapper.convertValue(
                handler.render(projectId, resourceId, null), MAP));
        if (rendered.get("media") instanceof List<?> media) {
            rendered.put("media", media.stream()
                    .filter(binding -> !(binding instanceof Map<?, ?> b && "INLINE".equals(b.get("usage"))))
                    .toList());
        }
        return normalize(read(handler, rendered));
    }

    /** The record back as a map, so the stored payload is normalized to the fields the kind knows. */
    public Map<String, Object> normalize(Object state) {
        return objectMapper.convertValue(state, MAP);
    }
}
