package eu.uniche.iguide.category.application;

import eu.uniche.iguide.category.domain.Category;
import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.category.domain.CategoryTranslation;
import eu.uniche.iguide.category.infrastructure.CategoryRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.resource.application.PageRequests;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import jakarta.persistence.criteria.Subquery;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * Pages through a Project's Categories in {@code sortOrder} ({@code 06} section 16), filtered by status,
 * scope, and {@code q}, a case-insensitive match on the name in any language or the code.
 */
@Service
public class ListCategoriesQuery {

    private final ProjectAccessGateway accessGateway;
    private final CategoryRepository categories;
    private final CategoryRules rules;

    public ListCategoriesQuery(ProjectAccessGateway accessGateway, CategoryRepository categories, CategoryRules rules) {
        this.accessGateway = accessGateway;
        this.categories = categories;
        this.rules = rules;
    }

    public Page<CategoryView> execute(UUID projectId, UnichePrincipal principal, ResourceStatus status,
                                      CategoryScope scope, String q, Integer page, Integer size) {
        accessGateway.requireAccess(projectId, principal);
        Specification<Category> spec = (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (scope != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("scope"), scope));
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.strip().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> {
                Subquery<UUID> named = query.subquery(UUID.class);
                var t = named.from(CategoryTranslation.class);
                named.select(t.get("categoryId")).where(
                        cb.equal(t.get("projectId"), projectId),
                        cb.equal(t.get("categoryId"), root.get("id")),
                        cb.like(cb.lower(t.get("name")), pattern, '\\'));
                return cb.or(cb.like(root.get("code"), pattern, '\\'), cb.exists(named));
            });
        }
        Page<Category> result = categories.findAll(spec, PageRequests.of(page, size, Sort.by("sortOrder", "createdAt")));
        return new PageImpl<>(rules.views(projectId, result.getContent()), result.getPageable(),
                result.getTotalElements());
    }
}
