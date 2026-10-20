package eu.uniche.iguide.category.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.resource.domain.ResourceReference;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Reads one Category, and what keeps it in use ({@code GET .../references}). */
@Service
public class GetCategoryQuery {

    private final ProjectAccessGateway accessGateway;
    private final CategoryRules rules;

    public GetCategoryQuery(ProjectAccessGateway accessGateway, CategoryRules rules) {
        this.accessGateway = accessGateway;
        this.rules = rules;
    }

    public CategoryView execute(UUID projectId, UUID categoryId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return rules.view(rules.requireCategory(projectId, categoryId));
    }

    public List<ResourceReference> references(UUID projectId, UUID categoryId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return rules.referencesTo(projectId, rules.requireCategory(projectId, categoryId));
    }
}
