package eu.uniche.iguide.map.api;

import eu.uniche.iguide.map.application.ChangeMapStatusUseCase;
import eu.uniche.iguide.map.application.CreateMapUseCase;
import eu.uniche.iguide.map.application.GetMapQuery;
import eu.uniche.iguide.map.application.MapPlacementUseCase;
import eu.uniche.iguide.map.application.UpdateMapUseCase;
import eu.uniche.iguide.map.domain.GeographicMap;
import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.platform.web.PageResponse;
import eu.uniche.iguide.resource.api.ChangeStatusRequest;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.workingcopy.application.WorkingCopyStore;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
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

/** Geographic Maps and the Resources placed on them ({@code 07} section 14, {@code 12} section 7). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/maps")
public class MapController {

    private final GetMapQuery getMaps;
    private final CreateMapUseCase createMap;
    private final UpdateMapUseCase updateMap;
    private final ChangeMapStatusUseCase changeStatus;
    private final MapPlacementUseCase placements;
    private final WorkingCopyStore workingCopies;

    public MapController(GetMapQuery getMaps, CreateMapUseCase createMap, UpdateMapUseCase updateMap,
                         ChangeMapStatusUseCase changeStatus, MapPlacementUseCase placements,
                         WorkingCopyStore workingCopies) {
        this.getMaps = getMaps;
        this.createMap = createMap;
        this.updateMap = updateMap;
        this.changeStatus = changeStatus;
        this.placements = placements;
        this.workingCopies = workingCopies;
    }

    @GetMapping
    public PageResponse<MapResponse> list(@PathVariable UUID projectId,
                                          @RequestParam(required = false) ResourceStatus status,
                                          @RequestParam(required = false) Boolean pending,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        Page<GeographicMap> result = getMaps.list(projectId, CurrentPrincipal.require(), status,
                Boolean.TRUE.equals(pending), page, size);
        Map<UUID, Instant> copies = workingCopies.updatedAt(projectId, WorkingCopyResourceType.GEOGRAPHIC_MAP,
                result.map(GeographicMap::getId).getContent());
        return PageResponse.of(result, map -> MapResponse.from(map, copies.get(map.getId())));
    }

    @PostMapping
    public ResponseEntity<MapResponse> create(@PathVariable UUID projectId, @RequestBody MapRequests.Create request) {
        GeographicMap created = createMap.execute(projectId, CurrentPrincipal.require(), request.view(),
                request.area(), request.osmId());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/maps/" + created.getId()))
                .body(MapResponse.from(created));
    }

    @GetMapping("/{mapId}")
    public MapResponse get(@PathVariable UUID projectId, @PathVariable UUID mapId) {
        return MapResponse.from(getMaps.get(projectId, mapId, CurrentPrincipal.require()),
                workingCopies.updatedAt(projectId, WorkingCopyResourceType.GEOGRAPHIC_MAP, mapId));
    }

    @PutMapping("/{mapId}")
    public MapResponse update(@PathVariable UUID projectId, @PathVariable UUID mapId,
                              @Valid @RequestBody MapRequests.Update request) {
        return MapResponse.from(updateMap.execute(projectId, mapId, CurrentPrincipal.require(), request.version(),
                request.view(), request.visibility(), request.visitorFilterEnabled()));
    }

    @PostMapping("/{mapId}/status")
    public MapResponse changeStatus(@PathVariable UUID projectId, @PathVariable UUID mapId,
                                    @Valid @RequestBody ChangeStatusRequest request) {
        return MapResponse.from(changeStatus.execute(projectId, mapId, CurrentPrincipal.require(),
                request.targetStatus(), request.version()));
    }

    @GetMapping("/{mapId}/references")
    public List<ResourceReference> references(@PathVariable UUID projectId, @PathVariable UUID mapId) {
        return getMaps.references(projectId, mapId, CurrentPrincipal.require());
    }

    // --- Placements ------------------------------------------------------------------------------

    @GetMapping("/{mapId}/resources")
    public List<MapResponse.Placement> listPlacements(@PathVariable UUID projectId, @PathVariable UUID mapId) {
        return getMaps.placements(projectId, mapId, CurrentPrincipal.require()).stream()
                .map(MapResponse.Placement::from).toList();
    }

    @PostMapping("/{mapId}/resources")
    public ResponseEntity<MapResponse.Placement> place(@PathVariable UUID projectId, @PathVariable UUID mapId,
                                                       @RequestBody MapRequests.Place request) {
        var created = placements.place(projectId, mapId, CurrentPrincipal.require(), request.resourceType(),
                request.resourceId(), request.latitude(), request.longitude());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/maps/" + mapId
                + "/resources/" + created.getId())).body(MapResponse.Placement.from(created));
    }

    @PutMapping("/{mapId}/resources/{placementId}")
    public MapResponse.Placement move(@PathVariable UUID projectId, @PathVariable UUID mapId,
                                      @PathVariable UUID placementId, @RequestBody MapRequests.Move request) {
        return MapResponse.Placement.from(placements.move(projectId, mapId, placementId, CurrentPrincipal.require(),
                request.latitude(), request.longitude()));
    }

    @DeleteMapping("/{mapId}/resources/{placementId}")
    public ResponseEntity<Void> remove(@PathVariable UUID projectId, @PathVariable UUID mapId,
                                       @PathVariable UUID placementId) {
        placements.remove(projectId, mapId, placementId, CurrentPrincipal.require());
        return ResponseEntity.noContent().build();
    }
}
