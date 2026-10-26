package eu.uniche.iguide.translation.api;

import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.translation.application.ResourceTranslationUseCase;
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
 * One language of a Route Stop's or a Guide Block's texts ({@code 07} section 14, DECISIONS.md D-032). The versions
 * are the Route's or Guide's; the response is its read DTO, with {@code X-Working-Copy-Version} after a write into
 * its working copy, as for the Resource's own texts (D-031).
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class ChildTranslationController {

    private final ResourceTranslationUseCase translations;

    public ChildTranslationController(ResourceTranslationUseCase translations) {
        this.translations = translations;
    }

    @PutMapping("/routes/{routeId}/stops/{stopId}/translations/{language}")
    public ResponseEntity<Object> putStop(@PathVariable UUID projectId, @PathVariable UUID routeId,
                                          @PathVariable UUID stopId, @PathVariable String language,
                                          @RequestBody Map<String, Object> body) {
        return put(projectId, WorkingCopyResourceType.ROUTE, routeId, "stops", stopId, language, body);
    }

    @DeleteMapping("/routes/{routeId}/stops/{stopId}/translations/{language}")
    public ResponseEntity<Object> clearStop(@PathVariable UUID projectId, @PathVariable UUID routeId,
                                            @PathVariable UUID stopId, @PathVariable String language,
                                            @RequestParam(required = false) Long version,
                                            @RequestParam(required = false) Long workingCopyVersion) {
        return ResourceTranslationController.response(translations.clearChild(projectId, WorkingCopyResourceType.ROUTE,
                routeId, "stops", stopId, CurrentPrincipal.require(), language, version, workingCopyVersion));
    }

    @PutMapping("/guides/{guideId}/blocks/{blockId}/translations/{language}")
    public ResponseEntity<Object> putBlock(@PathVariable UUID projectId, @PathVariable UUID guideId,
                                           @PathVariable UUID blockId, @PathVariable String language,
                                           @RequestBody Map<String, Object> body) {
        return put(projectId, WorkingCopyResourceType.GUIDE, guideId, "blocks", blockId, language, body);
    }

    @DeleteMapping("/guides/{guideId}/blocks/{blockId}/translations/{language}")
    public ResponseEntity<Object> clearBlock(@PathVariable UUID projectId, @PathVariable UUID guideId,
                                             @PathVariable UUID blockId, @PathVariable String language,
                                             @RequestParam(required = false) Long version,
                                             @RequestParam(required = false) Long workingCopyVersion) {
        return ResourceTranslationController.response(translations.clearChild(projectId, WorkingCopyResourceType.GUIDE,
                guideId, "blocks", blockId, CurrentPrincipal.require(), language, version, workingCopyVersion));
    }

    private ResponseEntity<Object> put(UUID projectId, WorkingCopyResourceType type, UUID parentId, String children,
                                       UUID childId, String language, Map<String, Object> body) {
        Map<String, Object> fields = new HashMap<>(body);
        Long version = ResourceTranslationController.number(fields.remove("version"));
        Long workingCopyVersion = ResourceTranslationController.number(fields.remove("workingCopyVersion"));
        return ResourceTranslationController.response(translations.putChild(projectId, type, parentId, children,
                childId, CurrentPrincipal.require(), language, version, workingCopyVersion, fields));
    }
}
