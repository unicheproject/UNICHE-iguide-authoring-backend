package eu.uniche.iguide.navigation.api;

import eu.uniche.iguide.navigation.application.NavigationQuery;
import eu.uniche.iguide.navigation.application.NavigationUseCase;
import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.resource.api.OrderRequest;
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
 * The visitor app's menus ({@code 03} section 13, {@code 07} section 14, {@code 12} section 3 Navigation editor).
 * Every write answers the whole Navigation as it now reads, items in tree order, so the editor redraws from one
 * answer (DECISIONS.md D-046).
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/navigations")
public class NavigationController {

    private final NavigationQuery query;
    private final NavigationUseCase useCase;

    public NavigationController(NavigationQuery query, NavigationUseCase useCase) {
        this.query = query;
        this.useCase = useCase;
    }

    @GetMapping
    public List<NavigationResponse> list(@PathVariable UUID projectId) {
        return query.list(projectId, CurrentPrincipal.require()).stream().map(NavigationResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<NavigationResponse> create(@PathVariable UUID projectId,
                                                     @RequestBody NavigationRequests.Create request) {
        NavigationResponse created = NavigationResponse.from(useCase.create(projectId, CurrentPrincipal.require(),
                request.context(), request.name()));
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/navigations/" + created.id()))
                .body(created);
    }

    @GetMapping("/{navigationId}")
    public NavigationResponse get(@PathVariable UUID projectId, @PathVariable UUID navigationId) {
        return NavigationResponse.from(query.get(projectId, navigationId, CurrentPrincipal.require()));
    }

    @PutMapping("/{navigationId}")
    public NavigationResponse update(@PathVariable UUID projectId, @PathVariable UUID navigationId,
                                     @Valid @RequestBody NavigationRequests.Update request) {
        return NavigationResponse.from(useCase.update(projectId, navigationId, CurrentPrincipal.require(),
                request.version(), request.name(), request.configuration()));
    }

    // --- Items -----------------------------------------------------------------------------------

    @PostMapping("/{navigationId}/items")
    public ResponseEntity<NavigationResponse> addItem(@PathVariable UUID projectId, @PathVariable UUID navigationId,
                                                      @RequestBody NavigationRequests.Item request) {
        NavigationResponse navigation = NavigationResponse.from(useCase.addItem(projectId, navigationId,
                CurrentPrincipal.require(), request.input()));
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/navigations/" + navigationId))
                .body(navigation);
    }

    @PutMapping("/{navigationId}/items/{itemId}")
    public NavigationResponse updateItem(@PathVariable UUID projectId, @PathVariable UUID navigationId,
                                         @PathVariable UUID itemId, @RequestBody NavigationRequests.Item request) {
        return NavigationResponse.from(useCase.updateItem(projectId, navigationId, itemId, CurrentPrincipal.require(),
                request.version(), request.input()));
    }

    @DeleteMapping("/{navigationId}/items/{itemId}")
    public NavigationResponse removeItem(@PathVariable UUID projectId, @PathVariable UUID navigationId,
                                         @PathVariable UUID itemId, @RequestParam(required = false) Long version) {
        return NavigationResponse.from(useCase.removeItem(projectId, navigationId, itemId, CurrentPrincipal.require(),
                version));
    }

    @PutMapping("/{navigationId}/items/{itemId}/translations/{language}")
    public NavigationResponse putLabel(@PathVariable UUID projectId, @PathVariable UUID navigationId,
                                       @PathVariable UUID itemId, @PathVariable String language,
                                       @Valid @RequestBody NavigationRequests.Label request) {
        return NavigationResponse.from(useCase.putLabel(projectId, navigationId, itemId, CurrentPrincipal.require(),
                language, request.version(), request.label()));
    }

    @DeleteMapping("/{navigationId}/items/{itemId}/translations/{language}")
    public NavigationResponse clearLabel(@PathVariable UUID projectId, @PathVariable UUID navigationId,
                                         @PathVariable UUID itemId, @PathVariable String language,
                                         @RequestParam(required = false) Long version) {
        return NavigationResponse.from(useCase.putLabel(projectId, navigationId, itemId, CurrentPrincipal.require(),
                language, version, null));
    }

    @PutMapping("/{navigationId}/items:order")
    public NavigationResponse reorder(@PathVariable UUID projectId, @PathVariable UUID navigationId,
                                      @Valid @RequestBody OrderRequest request) {
        return NavigationResponse.from(useCase.reorder(projectId, navigationId, CurrentPrincipal.require(),
                request.ids()));
    }
}
