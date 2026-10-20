package eu.uniche.iguide.route.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.resource.domain.ResourceReference;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Reads one Route, its stops, and the rows that reference it ({@code GET .../references}). */
@Service
public class GetRouteQuery {

    private final ProjectAccessGateway accessGateway;
    private final RouteRules rules;
    private final List<RouteReferenceSource> referenceSources;

    public GetRouteQuery(ProjectAccessGateway accessGateway, RouteRules rules,
                         List<RouteReferenceSource> referenceSources) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.referenceSources = referenceSources;
    }

    public RouteView execute(UUID projectId, UUID routeId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return rules.view(rules.requireRoute(projectId, routeId));
    }

    public List<StopView> stops(UUID projectId, UUID routeId, UnichePrincipal principal) {
        return execute(projectId, routeId, principal).stops();
    }

    public List<ResourceReference> references(UUID projectId, UUID routeId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        rules.requireRoute(projectId, routeId);
        return referencesTo(projectId, routeId);
    }

    /** Rows of other kinds that refer to the Route ({@code 03} section 1), from every reference source. */
    List<ResourceReference> referencesTo(UUID projectId, UUID routeId) {
        return referenceSources.stream()
                .flatMap(source -> source.referencesTo(projectId, routeId).stream())
                .toList();
    }
}
