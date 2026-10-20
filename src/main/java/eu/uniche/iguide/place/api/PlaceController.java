package eu.uniche.iguide.place.api;

import eu.uniche.iguide.place.application.ChangePlaceStatusUseCase;
import eu.uniche.iguide.place.application.CreatePlaceCommand;
import eu.uniche.iguide.place.application.CreatePlaceUseCase;
import eu.uniche.iguide.place.application.GetPlaceQuery;
import eu.uniche.iguide.place.application.ListPlacesQuery;
import eu.uniche.iguide.place.application.PlaceTranslationInput;
import eu.uniche.iguide.place.application.PlaceView;
import eu.uniche.iguide.place.application.ReorderPlacesUseCase;
import eu.uniche.iguide.place.application.UpdatePlaceCommand;
import eu.uniche.iguide.place.application.UpdatePlaceUseCase;
import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.platform.web.PageResponse;
import eu.uniche.iguide.resource.api.ChangeStatusRequest;
import eu.uniche.iguide.resource.api.OrderRequest;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
 * Places of a Project ({@code 07} section 14). The class mapping stops at the Project so that
 * {@code places:order} can sit beside {@code places}.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class PlaceController {

    private final ListPlacesQuery listPlaces;
    private final GetPlaceQuery getPlace;
    private final CreatePlaceUseCase createPlace;
    private final UpdatePlaceUseCase updatePlace;
    private final ChangePlaceStatusUseCase changeStatus;
    private final ReorderPlacesUseCase reorderPlaces;

    public PlaceController(ListPlacesQuery listPlaces, GetPlaceQuery getPlace, CreatePlaceUseCase createPlace,
                           UpdatePlaceUseCase updatePlace, ChangePlaceStatusUseCase changeStatus,
                           ReorderPlacesUseCase reorderPlaces) {
        this.listPlaces = listPlaces;
        this.getPlace = getPlace;
        this.createPlace = createPlace;
        this.updatePlace = updatePlace;
        this.changeStatus = changeStatus;
        this.reorderPlaces = reorderPlaces;
    }

    @GetMapping("/places")
    public PageResponse<PlaceResponse> list(@PathVariable UUID projectId,
                                            @RequestParam(required = false) ResourceStatus status,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        return PageResponse.of(listPlaces.execute(projectId, CurrentPrincipal.require(), status, q, page, size),
                PlaceResponse::from);
    }

    @PostMapping("/places")
    public ResponseEntity<PlaceResponse> create(@PathVariable UUID projectId,
                                                @RequestBody CreatePlaceRequest request) {
        PlaceView created = createPlace.execute(projectId, CurrentPrincipal.require(), new CreatePlaceCommand(
                request.title(), request.slug(), request.type(), request.environment(), request.parentPlaceId()));
        return ResponseEntity
                .created(URI.create("/api/v1/projects/" + projectId + "/places/" + created.place().getId()))
                .body(PlaceResponse.from(created));
    }

    @GetMapping("/places/{placeId}")
    public PlaceResponse get(@PathVariable UUID projectId, @PathVariable UUID placeId) {
        return PlaceResponse.from(getPlace.execute(projectId, placeId, CurrentPrincipal.require()));
    }

    @PutMapping("/places/{placeId}")
    public PlaceResponse update(@PathVariable UUID projectId, @PathVariable UUID placeId,
                                @Valid @RequestBody UpdatePlaceRequest request) {
        Map<String, PlaceTranslationInput> translations = request.translations() == null ? Map.of()
                : request.translations().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().toInput()));
        return PlaceResponse.from(updatePlace.execute(projectId, placeId, CurrentPrincipal.require(),
                new UpdatePlaceCommand(request.version(), request.slug(), request.type(), request.environment(),
                        request.parentPlaceId(), request.internalName(), request.latitude(), request.longitude(),
                        request.featured(), translations)));
    }

    @PostMapping("/places/{placeId}/status")
    public PlaceResponse changeStatus(@PathVariable UUID projectId, @PathVariable UUID placeId,
                                      @Valid @RequestBody ChangeStatusRequest request) {
        return PlaceResponse.from(changeStatus.execute(projectId, placeId, CurrentPrincipal.require(),
                request.targetStatus(), request.version()));
    }

    @GetMapping("/places/{placeId}/references")
    public List<ResourceReference> references(@PathVariable UUID projectId, @PathVariable UUID placeId) {
        return getPlace.references(projectId, placeId, CurrentPrincipal.require());
    }

    @PutMapping("/places:order")
    public ResponseEntity<Void> reorder(@PathVariable UUID projectId, @Valid @RequestBody OrderRequest request) {
        reorderPlaces.execute(projectId, CurrentPrincipal.require(), request.ids());
        return ResponseEntity.noContent().build();
    }
}
