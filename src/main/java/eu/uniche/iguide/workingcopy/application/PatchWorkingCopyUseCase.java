package eu.uniche.iguide.workingcopy.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.resource.application.ErrorPaths;
import eu.uniche.iguide.workingcopy.domain.ResourceWorkingCopy;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import eu.uniche.iguide.workingcopy.infrastructure.ResourceWorkingCopyRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A child write on an ACTIVE parent, which {@code 07} section 14 makes "a write into the parent's
 * ResourceWorkingCopy, never into the live child rows" (DECISIONS.md D-044). The copy's payload, or the parent's
 * live state when it has no copy yet, is changed by the caller's edit and saved through
 * {@link SaveWorkingCopyUseCase}, whose dry run validates it like any save.
 *
 * <p>A child endpoint carries no versions: the edit changes one child and keeps the rest of the payload as it was
 * read, and the save is made under the versions read with it, so a copy saved by someone else in between is a
 * {@code 409 VERSION_CONFLICT}, never overwritten. The caller has passed the Project access check. Used by the
 * child endpoints of maps and floor plans (DECISIONS.md D-044) and of Routes, Guides, and Events (D-045).
 */
@Service
public class PatchWorkingCopyUseCase {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final WorkingCopyHandlers handlers;
    private final ResourceWorkingCopyRepository copies;
    private final SaveWorkingCopyUseCase saveWorkingCopy;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public PatchWorkingCopyUseCase(WorkingCopyHandlers handlers, ResourceWorkingCopyRepository copies,
                                   SaveWorkingCopyUseCase saveWorkingCopy, ObjectMapper objectMapper,
                                   TransactionTemplate transactionTemplate) {
        this.handlers = handlers;
        this.copies = copies;
        this.saveWorkingCopy = saveWorkingCopy;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    private record Start(Map<String, Object> payload, long baseVersion, Long workingCopyVersion) {
    }

    /**
     * What an edit returns: the endpoint's answer and the payload path of the child it changed, such as
     * {@code stops[2]}, under which the save's errors are reported relative to the child, as on a DRAFT parent.
     */
    public record Patched<R>(R result, String childPath) {
    }

    /** Applies {@code edit} to the payload, saves the copy, and returns what the edit returned. */
    public <R> R execute(UUID projectId, WorkingCopyResourceType type, UUID resourceId, UnichePrincipal principal,
                         Function<Map<String, Object>, Patched<R>> edit) {
        WorkingCopyHandler<Object> handler = handlers.handler(type);
        Start start = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            LiveResource live = handler.lock(projectId, resourceId);
            SaveWorkingCopyUseCase.requireActive(live, type);
            Optional<ResourceWorkingCopy> copy = copies.findByProjectIdAndResourceTypeAndResourceId(projectId, type,
                    resourceId);
            return copy.isEmpty()
                    ? new Start(handlers.liveState(handler, projectId, resourceId), live.version(), null)
                    : new Start(handlers.normalize(handlers.read(handler, copy.get().getPayload())),
                            copy.get().getBaseVersion(), copy.get().getVersion());
        });
        Map<String, Object> payload = new LinkedHashMap<>(start.payload());
        Patched<R> patched = edit.apply(payload);
        ErrorPaths.outside(patched.childPath(), () -> saveWorkingCopy.execute(projectId, type, resourceId, principal,
                start.baseVersion(), start.workingCopyVersion(), payload));
        return patched.result();
    }

    /** The payload's list of children under {@code key}, as mutable maps put back into the payload. */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> children(Map<String, Object> payload, String key) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (payload.get(key) instanceof List<?> list) {
            list.forEach(item -> items.add(new LinkedHashMap<>((Map<String, Object>) item)));
        }
        payload.put(key, items);
        return items;
    }

    /** A child record as a payload entry, with the field names the payload uses. */
    public Map<String, Object> entry(Object child) {
        return new LinkedHashMap<>(objectMapper.convertValue(child, MAP));
    }

    /** A payload entry as the child record. */
    public <T> T read(Map<String, Object> entry, Class<T> type) {
        return objectMapper.convertValue(entry, type);
    }

    /** The child with that id, if the list has one. */
    public static Optional<Map<String, Object>> child(List<Map<String, Object>> items, UUID id) {
        return items.stream().filter(item -> id.toString().equals(String.valueOf(item.get("id")))).findFirst();
    }
}
