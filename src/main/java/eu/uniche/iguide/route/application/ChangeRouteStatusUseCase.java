package eu.uniche.iguide.route.application;

import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceInUseException;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.workingcopy.application.WorkingCopyStore;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.domain.RouteStop;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies one lifecycle transition ({@code 03} section 1). Activation needs the default-language title
 * and at least one stop whose Point of Interest is ACTIVE. Deactivation and archival are refused with
 * {@code RESOURCE_IN_USE} while a non-archived Guide references the Route. Leaving ACTIVE discards the Route's
 * working copy ({@code 03} section 1).
 */
@Service
public class ChangeRouteStatusUseCase {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final RouteRules rules;
    private final RouteRepository routes;
    private final GetRouteQuery getRoute;
    private final WorkingCopyStore workingCopies;
    private final TransactionTemplate transactionTemplate;

    public ChangeRouteStatusUseCase(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages,
                                    RouteRules rules, RouteRepository routes, GetRouteQuery getRoute,
                                    WorkingCopyStore workingCopies, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.rules = rules;
        this.routes = routes;
        this.getRoute = getRoute;
        this.workingCopies = workingCopies;
        this.transactionTemplate = transactionTemplate;
    }

    public RouteView execute(UUID projectId, UUID routeId, UnichePrincipal principal, ResourceStatus target,
                             Long version) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Route route = rules.requireRoute(projectId, routeId);
            ResourceRules.requireCurrentVersion(route.getVersion(), version, "route");
            ResourceRules.requireTransition(route.getStatus(), target, "route");
            if (target == ResourceStatus.ACTIVE) {
                requireComplete(projectId, route);
            }
            if (route.getStatus().leavesVisitorsFor(target)) {
                List<ResourceReference> references = getRoute.referencesTo(projectId, routeId);
                if (!references.isEmpty()) {
                    throw new ResourceInUseException("The route is still used by other resources.", references);
                }
            }
            if (target != ResourceStatus.ACTIVE) {
                workingCopies.dropFor(projectId, WorkingCopyResourceType.ROUTE, routeId);
            }
            route.changeStatus(target, principal.subject(), Instant.now());
            return rules.view(routes.saveAndFlush(route));
        });
    }

    private void requireComplete(UUID projectId, Route route) {
        String defaultLanguage = languages.requireDefaultLanguage(projectId);
        RouteView view = rules.view(route);
        List<ErrorItem> missing = new ArrayList<>();
        boolean titled = view.translations().stream()
                .anyMatch(t -> t.getLanguageCode().equals(defaultLanguage) && t.hasTitle());
        if (!titled) {
            missing.add(new ErrorItem("translations." + defaultLanguage + ".title", "MISSING_REQUIRED_FIELDS",
                    "Is required in the default language."));
        }
        List<RouteStop> stops = view.stops().stream().map(StopView::stop).toList();
        Map<UUID, PointOfInterest> pois = rules.poisOf(projectId, stops);
        boolean anActiveStop = stops.stream().map(s -> pois.get(s.getPoiId()))
                .anyMatch(p -> p != null && p.getStatus() == ResourceStatus.ACTIVE);
        if (!anActiveStop) {
            missing.add(new ErrorItem("stops", "MISSING_REQUIRED_FIELDS",
                    "At least one stop must be an ACTIVE point of interest."));
        }
        if (!missing.isEmpty()) {
            throw new ValidationFailedException("The route is not complete.", missing);
        }
    }
}
