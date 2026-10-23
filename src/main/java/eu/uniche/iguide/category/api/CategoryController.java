package eu.uniche.iguide.category.api;

import eu.uniche.iguide.category.application.CategoryView;
import eu.uniche.iguide.category.application.ChangeCategoryStatusUseCase;
import eu.uniche.iguide.category.application.CreateCategoryUseCase;
import eu.uniche.iguide.category.application.GetCategoryQuery;
import eu.uniche.iguide.category.application.ListCategoriesQuery;
import eu.uniche.iguide.category.application.ReorderCategoriesUseCase;
import eu.uniche.iguide.category.application.UpdateCategoryCommand;
import eu.uniche.iguide.category.application.UpdateCategoryUseCase;
import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.platform.web.PageResponse;
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
 * Categories of a Project ({@code 07} section 14). The class mapping stops at the Project so that
 * {@code categories:order} sits beside {@code categories}.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class CategoryController {

    private final ListCategoriesQuery listCategories;
    private final GetCategoryQuery getCategory;
    private final CreateCategoryUseCase createCategory;
    private final UpdateCategoryUseCase updateCategory;
    private final ChangeCategoryStatusUseCase changeStatus;
    private final ReorderCategoriesUseCase reorderCategories;
    private final WorkingCopyStore workingCopies;

    public CategoryController(ListCategoriesQuery listCategories, GetCategoryQuery getCategory,
                              CreateCategoryUseCase createCategory, UpdateCategoryUseCase updateCategory,
                              ChangeCategoryStatusUseCase changeStatus, ReorderCategoriesUseCase reorderCategories,
                              WorkingCopyStore workingCopies) {
        this.listCategories = listCategories;
        this.getCategory = getCategory;
        this.createCategory = createCategory;
        this.updateCategory = updateCategory;
        this.changeStatus = changeStatus;
        this.reorderCategories = reorderCategories;
        this.workingCopies = workingCopies;
    }

    @GetMapping("/categories")
    public PageResponse<CategoryResponse> list(@PathVariable UUID projectId,
                                               @RequestParam(required = false) ResourceStatus status,
                                               @RequestParam(required = false) CategoryScope scope,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(required = false) String language,
                                               @RequestParam(required = false) String sort,
                                               @RequestParam(required = false) Boolean pending,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        Page<CategoryView> result = listCategories.execute(projectId, CurrentPrincipal.require(), scope, new ListParams(status, q,
                language, null, Boolean.TRUE.equals(pending), sort, page, size));
        Map<UUID, Instant> copies = workingCopies.updatedAt(projectId, WorkingCopyResourceType.CATEGORY,
                result.map(view -> view.category().getId()).getContent());
        return PageResponse.of(result, view -> CategoryResponse.from(view, copies.get(view.category().getId())));
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> create(@PathVariable UUID projectId,
                                                   @RequestBody CategoryRequests.Create request) {
        CategoryView created = createCategory.execute(projectId, CurrentPrincipal.require(), request.name(),
                request.code(), request.scope(), request.parentCategoryId());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/categories/"
                + created.category().getId())).body(CategoryResponse.from(created));
    }

    @GetMapping("/categories/{categoryId}")
    public CategoryResponse get(@PathVariable UUID projectId, @PathVariable UUID categoryId) {
        return CategoryResponse.from(getCategory.execute(projectId, categoryId, CurrentPrincipal.require()),
                workingCopies.updatedAt(projectId, WorkingCopyResourceType.CATEGORY, categoryId));
    }

    @PutMapping("/categories/{categoryId}")
    public CategoryResponse update(@PathVariable UUID projectId, @PathVariable UUID categoryId,
                                   @Valid @RequestBody CategoryRequests.Update request) {
        return CategoryResponse.from(updateCategory.execute(projectId, categoryId, CurrentPrincipal.require(),
                new UpdateCategoryCommand(request.version(), request.code(), request.scope(), request.parentCategoryId(),
                        request.icon(), request.translationInputs())));
    }

    @PostMapping("/categories/{categoryId}/status")
    public CategoryResponse changeStatus(@PathVariable UUID projectId, @PathVariable UUID categoryId,
                                         @Valid @RequestBody ChangeStatusRequest request) {
        return CategoryResponse.from(changeStatus.execute(projectId, categoryId, CurrentPrincipal.require(),
                request.targetStatus(), request.version()));
    }

    @GetMapping("/categories/{categoryId}/references")
    public List<ResourceReference> references(@PathVariable UUID projectId, @PathVariable UUID categoryId) {
        return getCategory.references(projectId, categoryId, CurrentPrincipal.require());
    }

    @PutMapping("/categories:order")
    public ResponseEntity<Void> reorder(@PathVariable UUID projectId, @Valid @RequestBody OrderRequest request) {
        reorderCategories.execute(projectId, CurrentPrincipal.require(), request.ids());
        return ResponseEntity.noContent().build();
    }
}
