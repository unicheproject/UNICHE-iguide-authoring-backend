package eu.uniche.iguide.poi.api;

import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.platform.web.PageResponse;
import eu.uniche.iguide.poi.application.ChangePoiStatusUseCase;
import eu.uniche.iguide.poi.application.CreatePoiCommand;
import eu.uniche.iguide.poi.application.CreatePoiUseCase;
import eu.uniche.iguide.poi.application.GetPoiQuery;
import eu.uniche.iguide.poi.application.ListPoisQuery;
import eu.uniche.iguide.poi.application.PoiTranslationInput;
import eu.uniche.iguide.poi.application.PoiView;
import eu.uniche.iguide.poi.application.ReorderPoisUseCase;
import eu.uniche.iguide.poi.application.UpdatePoiCommand;
import eu.uniche.iguide.poi.application.UpdatePoiUseCase;
import eu.uniche.iguide.resource.api.ChangeStatusRequest;
import eu.uniche.iguide.resource.api.OrderRequest;
import eu.uniche.iguide.resource.application.ListParams;
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
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
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
 * Points of Interest of a Project ({@code 07} section 14). The class mapping stops at the Project so that
 * {@code pois:order} can sit beside {@code pois}.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class PoiController {

    private final ListPoisQuery listPois;
    private final GetPoiQuery getPoi;
    private final CreatePoiUseCase createPoi;
    private final UpdatePoiUseCase updatePoi;
    private final ChangePoiStatusUseCase changeStatus;
    private final ReorderPoisUseCase reorderPois;
    private final WorkingCopyStore workingCopies;

    public PoiController(ListPoisQuery listPois, GetPoiQuery getPoi, CreatePoiUseCase createPoi,
                         UpdatePoiUseCase updatePoi, ChangePoiStatusUseCase changeStatus,
                         ReorderPoisUseCase reorderPois, WorkingCopyStore workingCopies) {
        this.listPois = listPois;
        this.getPoi = getPoi;
        this.createPoi = createPoi;
        this.updatePoi = updatePoi;
        this.changeStatus = changeStatus;
        this.reorderPois = reorderPois;
        this.workingCopies = workingCopies;
    }

    @GetMapping("/pois")
    public PageResponse<PoiResponse> list(@PathVariable UUID projectId,
                                          @RequestParam(required = false) ResourceStatus status,
                                          @RequestParam(required = false) UUID placeId,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(required = false) String language,
                                          @RequestParam(required = false) Boolean featured,
                                          @RequestParam(required = false) String categoryId,
                                          @RequestParam(required = false) String sort,
                                          @RequestParam(required = false) Boolean pending,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        Page<PoiView> result = listPois.execute(projectId, CurrentPrincipal.require(), placeId, categoryId, new ListParams(status, q,
                language, featured, Boolean.TRUE.equals(pending), sort, page, size));
        Map<UUID, Instant> copies = workingCopies.updatedAt(projectId, WorkingCopyResourceType.POI,
                result.map(view -> view.poi().getId()).getContent());
        return PageResponse.of(result, view -> PoiResponse.from(view, copies.get(view.poi().getId())));
    }

    @PostMapping("/pois")
    public ResponseEntity<PoiResponse> create(@PathVariable UUID projectId, @RequestBody CreatePoiRequest request) {
        PoiView created = createPoi.execute(projectId, CurrentPrincipal.require(),
                new CreatePoiCommand(request.title(), request.slug(), request.placeId(), request.code()));
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/pois/" + created.poi().getId()))
                .body(PoiResponse.from(created));
    }

    @GetMapping("/pois/{poiId}")
    public PoiResponse get(@PathVariable UUID projectId, @PathVariable UUID poiId) {
        return PoiResponse.from(getPoi.execute(projectId, poiId, CurrentPrincipal.require()),
                workingCopies.updatedAt(projectId, WorkingCopyResourceType.POI, poiId));
    }

    @PutMapping("/pois/{poiId}")
    public PoiResponse update(@PathVariable UUID projectId, @PathVariable UUID poiId,
                              @Valid @RequestBody UpdatePoiRequest request) {
        Map<String, PoiTranslationInput> translations = inputs(request.translations());
        return PoiResponse.from(updatePoi.execute(projectId, poiId, CurrentPrincipal.require(),
                new UpdatePoiCommand(request.version(), request.slug(), request.placeId(), request.code(),
                        request.internalName(), request.latitude(), request.longitude(),
                        request.estimatedVisitMinutes(), request.featured(), translations)));
    }

    @PostMapping("/pois/{poiId}/status")
    public PoiResponse changeStatus(@PathVariable UUID projectId, @PathVariable UUID poiId,
                                    @Valid @RequestBody ChangeStatusRequest request) {
        return PoiResponse.from(changeStatus.execute(projectId, poiId, CurrentPrincipal.require(),
                request.targetStatus(), request.version()));
    }

    @GetMapping("/pois/{poiId}/references")
    public List<ResourceReference> references(@PathVariable UUID projectId, @PathVariable UUID poiId) {
        return getPoi.references(projectId, poiId, CurrentPrincipal.require());
    }

    static Map<String, PoiTranslationInput> inputs(Map<String, PoiTranslationBody> translations) {
        return translations == null ? Map.of() : translations.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toInput()));
    }

    @PutMapping("/pois:order")
    public ResponseEntity<Void> reorder(@PathVariable UUID projectId, @Valid @RequestBody OrderRequest request) {
        reorderPois.execute(projectId, CurrentPrincipal.require(), request.ids());
        return ResponseEntity.noContent().build();
    }
}
