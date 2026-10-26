package eu.uniche.iguide.translation.api;

import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.translation.application.MachineTranslationUseCase;
import eu.uniche.iguide.translation.application.ProvenanceStore;
import eu.uniche.iguide.workingcopy.application.WorkingCopyHandlers;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Translate with MuseMind" and "mark reviewed" for one language of a Resource ({@code 12} section 3; DECISIONS.md
 * D-035); {@code kind} as in {@link ResourceTranslationController}.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/{kind}/{resourceId}/translations")
public class MachineTranslationController {

    private final WorkingCopyHandlers handlers;
    private final MachineTranslationUseCase machineTranslation;

    public MachineTranslationController(WorkingCopyHandlers handlers, MachineTranslationUseCase machineTranslation) {
        this.handlers = handlers;
        this.machineTranslation = machineTranslation;
    }

    /**
     * Body: {@code version}, and {@code workingCopyVersion} once a copy exists. The response is that of
     * {@code PUT .../translations/{lang}}: the read DTO, with {@code X-Working-Copy-Version} after a write into a copy.
     */
    @PostMapping("/{language}:translate")
    public ResponseEntity<Object> translate(@PathVariable UUID projectId, @PathVariable String kind,
                                            @PathVariable UUID resourceId, @PathVariable String language,
                                            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> versions = body == null ? Map.of() : body;
        return ResourceTranslationController.response(machineTranslation.translate(projectId,
                ResourceTranslationController.kindOf(handlers, kind), resourceId, CurrentPrincipal.require(), language,
                ResourceTranslationController.number(versions.get("version")),
                ResourceTranslationController.number(versions.get("workingCopyVersion"))));
    }

    /** No body needed: it changes no text, so no version. */
    @PostMapping("/{language}:mark-reviewed")
    public ProvenanceStore.Entry markReviewed(@PathVariable UUID projectId, @PathVariable String kind,
                                              @PathVariable UUID resourceId, @PathVariable String language) {
        return machineTranslation.markReviewed(projectId, ResourceTranslationController.kindOf(handlers, kind),
                resourceId, CurrentPrincipal.require(), language);
    }
}
