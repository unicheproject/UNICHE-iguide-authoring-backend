package eu.uniche.iguide.floorplan.api;

import eu.uniche.iguide.floorplan.application.ChangeFloorPlanStatusUseCase;
import eu.uniche.iguide.floorplan.application.CreateFloorPlanUseCase;
import eu.uniche.iguide.floorplan.application.FloorPlanPositionUseCase;
import eu.uniche.iguide.floorplan.application.GetFloorPlanQuery;
import eu.uniche.iguide.floorplan.application.ReorderFloorPlansUseCase;
import eu.uniche.iguide.floorplan.application.UpdateFloorPlanUseCase;
import eu.uniche.iguide.floorplan.domain.FloorPlan;
import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.platform.web.PageResponse;
import eu.uniche.iguide.resource.api.ChangeStatusRequest;
import eu.uniche.iguide.resource.api.OrderRequest;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Floor Plans and their positions ({@code 07} section 14, {@code 12} section 7). The class mapping stops
 * at the Project so that {@code floor-plans:order} can sit beside {@code floor-plans}.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class FloorPlanController {

    private final GetFloorPlanQuery getPlans;
    private final CreateFloorPlanUseCase createPlan;
    private final UpdateFloorPlanUseCase updatePlan;
    private final ChangeFloorPlanStatusUseCase changeStatus;
    private final ReorderFloorPlansUseCase reorderPlans;
    private final FloorPlanPositionUseCase positions;

    public FloorPlanController(GetFloorPlanQuery getPlans, CreateFloorPlanUseCase createPlan,
                               UpdateFloorPlanUseCase updatePlan, ChangeFloorPlanStatusUseCase changeStatus,
                               ReorderFloorPlansUseCase reorderPlans, FloorPlanPositionUseCase positions) {
        this.getPlans = getPlans;
        this.createPlan = createPlan;
        this.updatePlan = updatePlan;
        this.changeStatus = changeStatus;
        this.reorderPlans = reorderPlans;
        this.positions = positions;
    }

    @GetMapping("/floor-plans")
    public PageResponse<FloorPlanResponse> list(@PathVariable UUID projectId,
                                                @RequestParam(required = false) ResourceStatus status,
                                                @RequestParam(required = false) UUID placeId,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size) {
        return PageResponse.of(getPlans.list(projectId, CurrentPrincipal.require(), status, placeId, page, size),
                FloorPlanResponse::from);
    }

    @PostMapping("/floor-plans")
    public ResponseEntity<FloorPlanResponse> create(@PathVariable UUID projectId,
                                                    @RequestBody FloorPlanRequests.Create request) {
        FloorPlan created = createPlan.execute(projectId, CurrentPrincipal.require(), request.details());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/floor-plans/" + created.getId()))
                .body(FloorPlanResponse.from(created));
    }

    @GetMapping("/floor-plans/{planId}")
    public FloorPlanResponse get(@PathVariable UUID projectId, @PathVariable UUID planId) {
        return FloorPlanResponse.from(getPlans.get(projectId, planId, CurrentPrincipal.require()));
    }

    @PutMapping("/floor-plans/{planId}")
    public FloorPlanResponse update(@PathVariable UUID projectId, @PathVariable UUID planId,
                                    @Valid @RequestBody FloorPlanRequests.Update request) {
        return FloorPlanResponse.from(updatePlan.execute(projectId, planId, CurrentPrincipal.require(),
                request.version(), request.details()));
    }

    @PostMapping("/floor-plans/{planId}/status")
    public FloorPlanResponse changeStatus(@PathVariable UUID projectId, @PathVariable UUID planId,
                                          @Valid @RequestBody ChangeStatusRequest request) {
        return FloorPlanResponse.from(changeStatus.execute(projectId, planId, CurrentPrincipal.require(),
                request.targetStatus(), request.version()));
    }

    @GetMapping("/floor-plans/{planId}/references")
    public List<ResourceReference> references(@PathVariable UUID projectId, @PathVariable UUID planId) {
        return getPlans.references(projectId, planId, CurrentPrincipal.require());
    }

    @PutMapping("/floor-plans:order")
    public ResponseEntity<Void> reorder(@PathVariable UUID projectId, @Valid @RequestBody OrderRequest request) {
        reorderPlans.execute(projectId, CurrentPrincipal.require(), request.ids());
        return ResponseEntity.noContent().build();
    }

    // --- Positions -------------------------------------------------------------------------------

    @GetMapping("/floor-plans/{planId}/positions")
    public List<FloorPlanResponse.Position> listPositions(@PathVariable UUID projectId, @PathVariable UUID planId) {
        return getPlans.positions(projectId, planId, CurrentPrincipal.require()).stream()
                .map(FloorPlanResponse.Position::from).toList();
    }

    @PostMapping("/floor-plans/{planId}/positions")
    public ResponseEntity<FloorPlanResponse.Position> position(@PathVariable UUID projectId,
                                                               @PathVariable UUID planId,
                                                               @RequestBody FloorPlanRequests.Position request) {
        var created = positions.position(projectId, planId, CurrentPrincipal.require(), request.resourceType(),
                request.resourceId(), request.x(), request.y(), request.rotation());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/floor-plans/" + planId
                + "/positions/" + created.getId())).body(FloorPlanResponse.Position.from(created));
    }

    @PutMapping("/floor-plans/{planId}/positions/{positionId}")
    public FloorPlanResponse.Position move(@PathVariable UUID projectId, @PathVariable UUID planId,
                                           @PathVariable UUID positionId, @RequestBody FloorPlanRequests.Move request) {
        return FloorPlanResponse.Position.from(positions.move(projectId, planId, positionId, CurrentPrincipal.require(),
                request.x(), request.y(), request.rotation()));
    }

    @DeleteMapping("/floor-plans/{planId}/positions/{positionId}")
    public ResponseEntity<Void> remove(@PathVariable UUID projectId, @PathVariable UUID planId,
                                       @PathVariable UUID positionId) {
        positions.remove(projectId, planId, positionId, CurrentPrincipal.require());
        return ResponseEntity.noContent().build();
    }
}
