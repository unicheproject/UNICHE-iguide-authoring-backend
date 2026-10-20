package eu.uniche.iguide.route.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.resource.application.PageRequests;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.domain.RouteTranslation;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
import jakarta.persistence.criteria.Subquery;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * Pages through a Project's Routes in {@code sortOrder} ({@code 06} section 16), filtered by status and
 * by {@code q}, a case-insensitive match on the title in any language ({@code 07} section 14).
 */
@Service
public class ListRoutesQuery {

    private final ProjectAccessGateway accessGateway;
    private final RouteRepository routes;
    private final RouteRules rules;

    public ListRoutesQuery(ProjectAccessGateway accessGateway, RouteRepository routes, RouteRules rules) {
        this.accessGateway = accessGateway;
        this.routes = routes;
        this.rules = rules;
    }

    public Page<RouteView> execute(UUID projectId, UnichePrincipal principal, ResourceStatus status, String q,
                                   Integer page, Integer size) {
        accessGateway.requireAccess(projectId, principal);
        Specification<Route> spec = (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.strip().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> {
                Subquery<UUID> titled = query.subquery(UUID.class);
                var t = titled.from(RouteTranslation.class);
                titled.select(t.get("routeId")).where(
                        cb.equal(t.get("projectId"), projectId),
                        cb.equal(t.get("routeId"), root.get("id")),
                        cb.like(cb.lower(t.get("title")), pattern, '\\'));
                return cb.exists(titled);
            });
        }
        Page<Route> result = routes.findAll(spec, PageRequests.of(page, size, Sort.by("sortOrder", "createdAt")));
        return new PageImpl<>(rules.views(projectId, result.getContent()), result.getPageable(),
                result.getTotalElements());
    }
}
