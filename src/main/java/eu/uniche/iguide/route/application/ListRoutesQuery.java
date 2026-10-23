package eu.uniche.iguide.route.application;

import eu.uniche.iguide.category.application.CategoryFilter;
import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.resource.application.ListFilters;
import eu.uniche.iguide.resource.application.ListParams;
import eu.uniche.iguide.resource.application.PageRequests;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.domain.RouteTranslation;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
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
 * Pages through a Project's Routes in {@code sortOrder} ({@code 06} section 16), filtered by status and
 * by {@code q}, a case-insensitive match on the title in any language, and by {@code pending}, the Routes with
 * a working copy ({@code 07} section 14).
 *
 * <p>{@code language} narrows {@code q} to one language, {@code sort} names an indexed column, and the other
 * filters of {@link eu.uniche.iguide.resource.application.ListParams} apply (DECISIONS.md D-030).
 */
@Service
public class ListRoutesQuery {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final RouteRepository routes;
    private final RouteRules rules;

    public ListRoutesQuery(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages,
                           RouteRepository routes, RouteRules rules) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.routes = routes;
        this.rules = rules;
    }

    public Page<RouteView> execute(UUID projectId, UnichePrincipal principal, String categoryId, ListParams params) {
        accessGateway.requireAccess(projectId, principal);
        String language = ListFilters.requireLanguage(params.language(), languages.enabledCodes(projectId));
        Specification<Route> spec = (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
        if (params.status() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), params.status()));
        }
        if (params.featured() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("featured"), params.featured()));
        }
        Specification<Route> inCategory = CategoryFilter.of(projectId, categoryId, CategoryScope.ROUTE);
        if (inCategory != null) {
            spec = spec.and(inCategory);
        }
        if (params.pending()) {
            spec = spec.and(WorkingCopyStore.pending(projectId, WorkingCopyResourceType.ROUTE));
        }
        String pattern = ListFilters.likePattern(params.q());
        if (pattern != null) {
            spec = spec.and((root, query, cb) -> {
                var matched = ListFilters.translationMatches(root, query, cb, projectId, pattern, language,
                        RouteTranslation.class, "routeId", "title");
                return matched;
            });
        }
        PageRequest paging = PageRequests.of(params.page(), params.size(),
                ListFilters.sort(params.sort(), Set.of("sortOrder", "slug")));
        Page<Route> result = routes.findAll(spec, paging);
        return new PageImpl<>(rules.views(projectId, result.getContent()), result.getPageable(),
                result.getTotalElements());
    }
}
