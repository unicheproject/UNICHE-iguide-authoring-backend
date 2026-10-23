package eu.uniche.iguide.translation.api;

import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.translation.application.ResourceTranslationUseCase;
import eu.uniche.iguide.workingcopy.application.WorkingCopyHandlers;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One language of a Resource's texts ({@code 07} section 14, DECISIONS.md D-031). {@code kind} is one of
 * {@code places, pois, routes, guides, events, categories}. The response is the Resource's read DTO either way; when
 * the change went into a working copy, {@code X-Working-Copy-Version} carries the copy's version for the next change.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/{kind}/{resourceId}/translations/{language}")
public class ResourceTranslationController {

    static final String WORKING_COPY_VERSION = "X-Working-Copy-Version";

    private final WorkingCopyHandlers handlers;
    private final ResourceTranslationUseCase translations;

    public ResourceTranslationController(WorkingCopyHandlers handlers, ResourceTranslationUseCase translations) {
        this.handlers = handlers;
        this.translations = translations;
    }

    /** Body: the kind's translation fields, {@code version}, and {@code workingCopyVersion} once a copy exists. */
    @PutMapping
    public ResponseEntity<Object> put(@PathVariable UUID projectId, @PathVariable String kind,
                                      @PathVariable UUID resourceId, @PathVariable String language,
                                      @RequestBody Map<String, Object> body) {
        Map<String, Object> fields = new HashMap<>(body);
        Long version = number(fields.remove("version"));
        Long workingCopyVersion = number(fields.remove("workingCopyVersion"));
        return response(translations.put(projectId, kindOf(kind), resourceId, CurrentPrincipal.require(), language,
                version, workingCopyVersion, fields));
    }

    @DeleteMapping
    public ResponseEntity<Object> clear(@PathVariable UUID projectId, @PathVariable String kind,
                                        @PathVariable UUID resourceId, @PathVariable String language,
                                        @RequestParam(required = false) Long version,
                                        @RequestParam(required = false) Long workingCopyVersion) {
        return response(translations.clear(projectId, kindOf(kind), resourceId, CurrentPrincipal.require(), language,
                version, workingCopyVersion));
    }

    private WorkingCopyResourceType kindOf(String kind) {
        WorkingCopyResourceType type = handlers.typeOf(kind);
        if (!ResourceTranslationUseCase.KINDS.contains(type)) {
            throw new ResourceNotFoundException("This kind of resource has no translations.");
        }
        return type;
    }

    private static ResponseEntity<Object> response(ResourceTranslationUseCase.Result result) {
        ResponseEntity.BodyBuilder ok = ResponseEntity.ok();
        if (result.workingCopyVersion() != null) {
            ok.header(WORKING_COPY_VERSION, String.valueOf(result.workingCopyVersion()));
        }
        return ok.body(result.resource());
    }

    private static Long number(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }
}
