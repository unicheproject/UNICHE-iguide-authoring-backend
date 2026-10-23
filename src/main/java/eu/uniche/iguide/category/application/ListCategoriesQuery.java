package eu.uniche.iguide.category.application;

import eu.uniche.iguide.category.domain.Category;
import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.category.domain.CategoryTranslation;
import eu.uniche.iguide.category.infrastructure.CategoryRepository;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.resource.application.ListFilters;
import eu.uniche.iguide.resource.application.ListParams;
import eu.uniche.iguide.resource.application.PageRequests;
import eu.uniche.iguide.workingcopy.application.WorkingCopyStore;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * Pages through a Project's Categories in {@code sortOrder} ({@code 06} section 16), filtered by status,
 * scope, {@code q}, a case-insensitive match on the name in any language or the code, and {@code pending},
 * the Categories with a working copy.
 *
 * <p>{@code language} narrows {@code q} to one language, {@code sort} names an indexed column, and the other
 * filters of {@link eu.uniche.iguide.resource.application.ListParams} apply (DECISIONS.md D-030).
 */
@Service
public class ListCategoriesQuery {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final CategoryRepository categories;
    private final CategoryRules rules;

    public ListCategoriesQuery(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages,
                               CategoryRepository categories, CategoryRules rules) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.categories = categories;
        this.rules = rules;
    }

    public Page<CategoryView> execute(UUID projectId, UnichePrincipal principal, CategoryScope scope,
                                      ListParams params) {
        accessGateway.requireAccess(projectId, principal);
        String language = ListFilters.requireLanguage(params.language(), languages.enabledCodes(projectId));
        Specification<Category> spec = (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
        if (params.status() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), params.status()));
        }
        if (scope != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("scope"), scope));
        }
        if (params.pending()) {
            spec = spec.and(WorkingCopyStore.pending(projectId, WorkingCopyResourceType.CATEGORY));
        }
        String pattern = ListFilters.likePattern(params.q());
        if (pattern != null) {
            spec = spec.and((root, query, cb) -> {
                var matched = ListFilters.translationMatches(root, query, cb, projectId, pattern, language,
                        CategoryTranslation.class, "categoryId", "name");
                return cb.or(cb.like(cb.lower(root.get("code")), pattern, '\\'), matched);
            });
        }
        PageRequest paging = PageRequests.of(params.page(), params.size(),
                ListFilters.sort(params.sort(), Set.of("sortOrder", "code")));
        Page<Category> result = categories.findAll(spec, paging);
        return new PageImpl<>(rules.views(projectId, result.getContent()), result.getPageable(),
                result.getTotalElements());
    }
}
