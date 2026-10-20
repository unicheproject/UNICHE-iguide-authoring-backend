package eu.uniche.iguide.category.api;

import eu.uniche.iguide.category.application.ResourceCategoryUseCase;
import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.category.domain.ResourceCategory;
import eu.uniche.iguide.platform.security.CurrentPrincipal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Category of a Point of Interest or Route ({@code 07} section 14, {@code .../{kind}/{id}/categories}).
 * Only {@code pois} and {@code routes} can be categorized ({@code 03} section 10).
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class ResourceCategoryController {

    private final ResourceCategoryUseCase bindings;

    public ResourceCategoryController(ResourceCategoryUseCase bindings) {
        this.bindings = bindings;
    }

    @GetMapping("/pois/{resourceId}/categories")
    public List<CategoryResponse.Binding> listPoi(@PathVariable UUID projectId, @PathVariable UUID resourceId) {
        return list(projectId, CategoryScope.POI, resourceId);
    }

    @PostMapping("/pois/{resourceId}/categories")
    public ResponseEntity<CategoryResponse.Binding> bindPoi(@PathVariable UUID projectId, @PathVariable UUID resourceId,
                                                            @RequestBody CategoryRequests.Bind request) {
        return bind(projectId, CategoryScope.POI, resourceId, request, "pois");
    }

    @DeleteMapping("/pois/{resourceId}/categories/{bindingId}")
    public ResponseEntity<Void> unbindPoi(@PathVariable UUID projectId, @PathVariable UUID resourceId,
                                          @PathVariable UUID bindingId) {
        bindings.unbind(projectId, CategoryScope.POI, resourceId, bindingId, CurrentPrincipal.require());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/routes/{resourceId}/categories")
    public List<CategoryResponse.Binding> listRoute(@PathVariable UUID projectId, @PathVariable UUID resourceId) {
        return list(projectId, CategoryScope.ROUTE, resourceId);
    }

    @PostMapping("/routes/{resourceId}/categories")
    public ResponseEntity<CategoryResponse.Binding> bindRoute(@PathVariable UUID projectId,
                                                              @PathVariable UUID resourceId,
                                                              @RequestBody CategoryRequests.Bind request) {
        return bind(projectId, CategoryScope.ROUTE, resourceId, request, "routes");
    }

    @DeleteMapping("/routes/{resourceId}/categories/{bindingId}")
    public ResponseEntity<Void> unbindRoute(@PathVariable UUID projectId, @PathVariable UUID resourceId,
                                            @PathVariable UUID bindingId) {
        bindings.unbind(projectId, CategoryScope.ROUTE, resourceId, bindingId, CurrentPrincipal.require());
        return ResponseEntity.noContent().build();
    }

    private List<CategoryResponse.Binding> list(UUID projectId, CategoryScope kind, UUID resourceId) {
        return bindings.list(projectId, kind, resourceId, CurrentPrincipal.require()).stream()
                .map(CategoryResponse.Binding::from).toList();
    }

    private ResponseEntity<CategoryResponse.Binding> bind(UUID projectId, CategoryScope kind, UUID resourceId,
                                                          CategoryRequests.Bind request, String path) {
        ResourceCategory created = bindings.bind(projectId, kind, resourceId, CurrentPrincipal.require(),
                request.categoryId());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/" + path + "/" + resourceId
                + "/categories/" + created.getId())).body(CategoryResponse.Binding.from(created));
    }
}
