package eu.uniche.iguide.workingcopy.api;

import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.workingcopy.application.ApplyWorkingCopyUseCase;
import eu.uniche.iguide.workingcopy.application.DiscardWorkingCopyUseCase;
import eu.uniche.iguide.workingcopy.application.GetWorkingCopyQuery;
import eu.uniche.iguide.workingcopy.application.SaveWorkingCopyUseCase;
import eu.uniche.iguide.workingcopy.application.WorkingCopyHandlers;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Working copies of ACTIVE Resources ({@code 07} section 14). {@code kind} is one of {@code places, pois, routes,
 * guides, events, categories, maps, floor-plans}; any other is {@code 404}.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/{kind}/{resourceId}")
public class WorkingCopyController {

    private final WorkingCopyHandlers handlers;
    private final GetWorkingCopyQuery getWorkingCopy;
    private final SaveWorkingCopyUseCase saveWorkingCopy;
    private final ApplyWorkingCopyUseCase applyWorkingCopy;
    private final DiscardWorkingCopyUseCase discardWorkingCopy;

    public WorkingCopyController(WorkingCopyHandlers handlers, GetWorkingCopyQuery getWorkingCopy,
                                 SaveWorkingCopyUseCase saveWorkingCopy, ApplyWorkingCopyUseCase applyWorkingCopy,
                                 DiscardWorkingCopyUseCase discardWorkingCopy) {
        this.handlers = handlers;
        this.getWorkingCopy = getWorkingCopy;
        this.saveWorkingCopy = saveWorkingCopy;
        this.applyWorkingCopy = applyWorkingCopy;
        this.discardWorkingCopy = discardWorkingCopy;
    }

    @GetMapping("/working-copy")
    public WorkingCopyResponse get(@PathVariable UUID projectId, @PathVariable String kind,
                                   @PathVariable UUID resourceId) {
        return WorkingCopyResponse.from(getWorkingCopy.execute(projectId, handlers.typeOf(kind), resourceId,
                CurrentPrincipal.require()));
    }

    @PutMapping("/working-copy")
    public WorkingCopyResponse save(@PathVariable UUID projectId, @PathVariable String kind,
                                    @PathVariable UUID resourceId, @Valid @RequestBody SaveWorkingCopyRequest request) {
        return WorkingCopyResponse.from(saveWorkingCopy.execute(projectId, handlers.typeOf(kind), resourceId,
                CurrentPrincipal.require(), request.baseVersion(), request.workingCopyVersion(), request.payload()));
    }

    @PostMapping("/working-copy:apply")
    public Object apply(@PathVariable UUID projectId, @PathVariable String kind, @PathVariable UUID resourceId) {
        return applyWorkingCopy.execute(projectId, handlers.typeOf(kind), resourceId, CurrentPrincipal.require());
    }

    @DeleteMapping("/working-copy")
    public ResponseEntity<Void> discard(@PathVariable UUID projectId, @PathVariable String kind,
                                        @PathVariable UUID resourceId) {
        discardWorkingCopy.execute(projectId, handlers.typeOf(kind), resourceId, CurrentPrincipal.require());
        return ResponseEntity.noContent().build();
    }
}
