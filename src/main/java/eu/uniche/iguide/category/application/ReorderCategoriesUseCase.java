package eu.uniche.iguide.category.application;

import eu.uniche.iguide.category.domain.Category;
import eu.uniche.iguide.category.infrastructure.CategoryRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** {@code PUT .../categories:order}: the complete ordered list of the Project's Category ids ({@code 07} section 14). */
@Service
public class ReorderCategoriesUseCase {

    private final ProjectAccessGateway accessGateway;
    private final CategoryRepository categories;
    private final TransactionTemplate transactionTemplate;

    public ReorderCategoriesUseCase(ProjectAccessGateway accessGateway, CategoryRepository categories,
                                    TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.categories = categories;
        this.transactionTemplate = transactionTemplate;
    }

    public void execute(UUID projectId, UnichePrincipal principal, List<UUID> orderedIds) {
        accessGateway.requireAccess(projectId, principal);
        transactionTemplate.executeWithoutResult(status -> {
            Set<UUID> existing = categories.findByProjectId(projectId).stream().map(Category::getId)
                    .collect(Collectors.toSet());
            List<UUID> ids = orderedIds == null ? List.of() : orderedIds;
            if (ids.size() != existing.size() || !new HashSet<>(ids).equals(existing)) {
                throw new ValidationFailedException("The order must list every category of the project exactly once.",
                        List.of(new ErrorItem("ids", "INCOMPLETE_ORDER", "List every category of this project exactly once.")));
            }
            for (int i = 0; i < ids.size(); i++) {
                categories.updateSortOrder(projectId, ids.get(i), i + 1);
            }
        });
    }
}
