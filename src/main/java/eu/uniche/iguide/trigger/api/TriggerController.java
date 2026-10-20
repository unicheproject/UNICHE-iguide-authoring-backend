package eu.uniche.iguide.trigger.api;

import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.platform.web.PageResponse;
import eu.uniche.iguide.resource.api.ChangeStatusRequest;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.trigger.application.TriggerQuery;
import eu.uniche.iguide.trigger.application.TriggerUseCase;
import eu.uniche.iguide.trigger.domain.InteractionTrigger;
import eu.uniche.iguide.trigger.domain.TriggerTargetType;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Interaction Triggers of a Project ({@code 07} section 14). The CMS edits them inside the editor of their
 * target Resource ({@code 12} section 3); there is no Triggers screen.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/triggers")
public class TriggerController {

    private final TriggerQuery query;
    private final TriggerUseCase useCase;

    public TriggerController(TriggerQuery query, TriggerUseCase useCase) {
        this.query = query;
        this.useCase = useCase;
    }

    @GetMapping
    public PageResponse<TriggerResponse> list(@PathVariable UUID projectId,
                                              @RequestParam(required = false) TriggerTargetType targetResourceType,
                                              @RequestParam(required = false) UUID targetResourceId,
                                              @RequestParam(required = false) ResourceStatus status,
                                              @RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer size) {
        return PageResponse.of(query.list(projectId, CurrentPrincipal.require(), targetResourceType, targetResourceId,
                status, page, size), TriggerResponse::from);
    }

    @PostMapping
    public ResponseEntity<TriggerResponse> create(@PathVariable UUID projectId, @RequestBody TriggerRequests.Create request) {
        InteractionTrigger created = useCase.create(projectId, CurrentPrincipal.require(), request.targetResourceType(),
                request.targetResourceId(), request.triggerType(), request.code(), request.supportedSurfaces(),
                request.configuration());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/triggers/" + created.getId()))
                .body(TriggerResponse.from(created));
    }

    @GetMapping("/{triggerId}")
    public TriggerResponse get(@PathVariable UUID projectId, @PathVariable UUID triggerId) {
        return TriggerResponse.from(query.get(projectId, triggerId, CurrentPrincipal.require()));
    }

    @PutMapping("/{triggerId}")
    public TriggerResponse update(@PathVariable UUID projectId, @PathVariable UUID triggerId,
                                  @Valid @RequestBody TriggerRequests.Update request) {
        return TriggerResponse.from(useCase.update(projectId, triggerId, CurrentPrincipal.require(), request.version(),
                request.code(), request.supportedSurfaces(), request.configuration()));
    }

    @PostMapping("/{triggerId}/status")
    public TriggerResponse changeStatus(@PathVariable UUID projectId, @PathVariable UUID triggerId,
                                        @Valid @RequestBody ChangeStatusRequest request) {
        return TriggerResponse.from(useCase.changeStatus(projectId, triggerId, CurrentPrincipal.require(),
                request.targetStatus(), request.version()));
    }

    @GetMapping(path = "/{triggerId}/qr.png", produces = {MediaType.IMAGE_PNG_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<byte[]> qrImage(@PathVariable UUID projectId, @PathVariable UUID triggerId) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .body(query.qrImage(projectId, triggerId, CurrentPrincipal.require()));
    }
}
