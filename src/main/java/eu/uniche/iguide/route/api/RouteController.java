package eu.uniche.iguide.route.api;

import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.platform.web.PageResponse;
import eu.uniche.iguide.resource.api.ChangeStatusRequest;
import eu.uniche.iguide.resource.api.OrderRequest;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.route.application.ChangeRouteStatusUseCase;
import eu.uniche.iguide.route.application.CreateRouteUseCase;
import eu.uniche.iguide.route.application.GetRouteQuery;
import eu.uniche.iguide.route.application.ListRoutesQuery;
import eu.uniche.iguide.route.application.ReorderRoutesUseCase;
import eu.uniche.iguide.route.application.RouteStopUseCase;
import eu.uniche.iguide.route.application.RouteView;
import eu.uniche.iguide.route.application.StopView;
import eu.uniche.iguide.route.application.UpdateRouteCommand;
import eu.uniche.iguide.route.application.UpdateRouteUseCase;
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
 * Routes of a Project and their stops ({@code 07} section 14, {@code 12} section 7). The class mapping
 * stops at the Project so that {@code routes:order} and {@code stops:order} sit beside their collections.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class RouteController {

    private final ListRoutesQuery listRoutes;
    private final GetRouteQuery getRoute;
    private final CreateRouteUseCase createRoute;
    private final UpdateRouteUseCase updateRoute;
    private final ChangeRouteStatusUseCase changeStatus;
    private final ReorderRoutesUseCase reorderRoutes;
    private final RouteStopUseCase stops;

    public RouteController(ListRoutesQuery listRoutes, GetRouteQuery getRoute, CreateRouteUseCase createRoute,
                           UpdateRouteUseCase updateRoute, ChangeRouteStatusUseCase changeStatus,
                           ReorderRoutesUseCase reorderRoutes, RouteStopUseCase stops) {
        this.listRoutes = listRoutes;
        this.getRoute = getRoute;
        this.createRoute = createRoute;
        this.updateRoute = updateRoute;
        this.changeStatus = changeStatus;
        this.reorderRoutes = reorderRoutes;
        this.stops = stops;
    }

    @GetMapping("/routes")
    public PageResponse<RouteResponse> list(@PathVariable UUID projectId,
                                            @RequestParam(required = false) ResourceStatus status,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        return PageResponse.of(listRoutes.execute(projectId, CurrentPrincipal.require(), status, q, page, size),
                RouteResponse::from);
    }

    @PostMapping("/routes")
    public ResponseEntity<RouteResponse> create(@PathVariable UUID projectId,
                                                @RequestBody RouteRequests.Create request) {
        RouteView created = createRoute.execute(projectId, CurrentPrincipal.require(), request.title(),
                request.slug(), request.type());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/routes/" + created.route().getId()))
                .body(RouteResponse.from(created));
    }

    @GetMapping("/routes/{routeId}")
    public RouteResponse get(@PathVariable UUID projectId, @PathVariable UUID routeId) {
        return RouteResponse.from(getRoute.execute(projectId, routeId, CurrentPrincipal.require()));
    }

    @PutMapping("/routes/{routeId}")
    public RouteResponse update(@PathVariable UUID projectId, @PathVariable UUID routeId,
                                @Valid @RequestBody RouteRequests.Update request) {
        return RouteResponse.from(updateRoute.execute(projectId, routeId, CurrentPrincipal.require(),
                new UpdateRouteCommand(request.version(), request.slug(), request.type(), request.travelMode(),
                        request.difficulty(), request.estimatedDurationMinutes(), request.distanceMeters(),
                        request.loop(), request.internalName(), request.featured(), request.translationInputs())));
    }

    @PostMapping("/routes/{routeId}/status")
    public RouteResponse changeStatus(@PathVariable UUID projectId, @PathVariable UUID routeId,
                                      @Valid @RequestBody ChangeStatusRequest request) {
        return RouteResponse.from(changeStatus.execute(projectId, routeId, CurrentPrincipal.require(),
                request.targetStatus(), request.version()));
    }

    @GetMapping("/routes/{routeId}/references")
    public List<ResourceReference> references(@PathVariable UUID projectId, @PathVariable UUID routeId) {
        return getRoute.references(projectId, routeId, CurrentPrincipal.require());
    }

    @PutMapping("/routes:order")
    public ResponseEntity<Void> reorder(@PathVariable UUID projectId, @Valid @RequestBody OrderRequest request) {
        reorderRoutes.execute(projectId, CurrentPrincipal.require(), request.ids());
        return ResponseEntity.noContent().build();
    }

    // --- Stops -----------------------------------------------------------------------------------

    @GetMapping("/routes/{routeId}/stops")
    public List<RouteResponse.Stop> listStops(@PathVariable UUID projectId, @PathVariable UUID routeId) {
        return getRoute.stops(projectId, routeId, CurrentPrincipal.require()).stream()
                .map(RouteResponse.Stop::from).toList();
    }

    @PostMapping("/routes/{routeId}/stops")
    public ResponseEntity<RouteResponse.Stop> addStop(@PathVariable UUID projectId, @PathVariable UUID routeId,
                                                      @RequestBody RouteRequests.Stop request) {
        StopView created = stops.add(projectId, routeId, CurrentPrincipal.require(), request.poiId(),
                request.translationInputs());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/routes/" + routeId + "/stops/"
                + created.stop().getId())).body(RouteResponse.Stop.from(created));
    }

    @PutMapping("/routes/{routeId}/stops/{stopId}")
    public RouteResponse.Stop updateStop(@PathVariable UUID projectId, @PathVariable UUID routeId,
                                         @PathVariable UUID stopId, @RequestBody RouteRequests.Stop request) {
        return RouteResponse.Stop.from(stops.update(projectId, routeId, stopId, CurrentPrincipal.require(),
                request.poiId(), request.translationInputs()));
    }

    @DeleteMapping("/routes/{routeId}/stops/{stopId}")
    public ResponseEntity<Void> removeStop(@PathVariable UUID projectId, @PathVariable UUID routeId,
                                           @PathVariable UUID stopId) {
        stops.remove(projectId, routeId, stopId, CurrentPrincipal.require());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/routes/{routeId}/stops:order")
    public List<RouteResponse.Stop> reorderStops(@PathVariable UUID projectId, @PathVariable UUID routeId,
                                                 @Valid @RequestBody OrderRequest request) {
        return stops.reorder(projectId, routeId, CurrentPrincipal.require(), request.ids()).stream()
                .map(RouteResponse.Stop::from).toList();
    }
}
