package eu.uniche.iguide.category.api;

import eu.uniche.iguide.category.application.CategoryRules;
import eu.uniche.iguide.category.application.UpdateCategoryCommand;
import eu.uniche.iguide.category.application.UpdateCategoryUseCase;
import eu.uniche.iguide.category.domain.Category;
import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.workingcopy.application.LiveResource;
import eu.uniche.iguide.workingcopy.application.LiveRowLock;
import eu.uniche.iguide.workingcopy.application.WorkingCopyHandler;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The working copy of an ACTIVE Category: its update body without {@code version} (DECISIONS.md D-027). */
@Component
public class CategoryWorkingCopyHandler implements WorkingCopyHandler<CategoryWorkingCopyHandler.State> {

    public record State(String code, CategoryScope scope, UUID parentCategoryId, String icon,
                        Map<String, CategoryRequests.CategoryTranslationBody> translations) {
    }

    private final LiveRowLock lock;
    private final CategoryRules rules;
    private final UpdateCategoryUseCase updateCategory;

    public CategoryWorkingCopyHandler(LiveRowLock lock, CategoryRules rules, UpdateCategoryUseCase updateCategory) {
        this.lock = lock;
        this.rules = rules;
        this.updateCategory = updateCategory;
    }

    @Override
    public WorkingCopyResourceType type() {
        return WorkingCopyResourceType.CATEGORY;
    }

    @Override
    public Class<State> stateType() {
        return State.class;
    }

    @Override
    public LiveResource lock(UUID projectId, UUID resourceId) {
        Category category = lock.lock(Category.class, projectId, resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("No such category in this project."));
        return new LiveResource(category.getStatus(), category.getVersion());
    }

    @Override
    public void write(UUID projectId, UUID resourceId, UnichePrincipal principal, State state) {
        updateCategory.write(projectId, rules.requireCategory(projectId, resourceId), principal,
                new UpdateCategoryCommand(null, state.code(), state.scope(), state.parentCategoryId(), state.icon(),
                        CategoryRequests.inputs(state.translations())));
    }

    @Override
    public Object render(UUID projectId, UUID resourceId, Instant workingCopyUpdatedAt) {
        return CategoryResponse.from(rules.view(rules.requireCategory(projectId, resourceId)), workingCopyUpdatedAt);
    }
}
