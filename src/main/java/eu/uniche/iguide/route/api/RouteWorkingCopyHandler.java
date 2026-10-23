package eu.uniche.iguide.route.api;

import eu.uniche.iguide.asset.application.ResourceAssetUseCase;
import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.category.application.ResourceCategoryUseCase;
import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.route.application.RouteRules;
import eu.uniche.iguide.route.application.RouteStopUseCase;
import eu.uniche.iguide.route.application.UpdateRouteCommand;
import eu.uniche.iguide.route.application.UpdateRouteUseCase;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.domain.RouteDifficulty;
import eu.uniche.iguide.route.domain.RouteType;
import eu.uniche.iguide.route.domain.TravelMode;
import eu.uniche.iguide.workingcopy.application.LiveResource;
import eu.uniche.iguide.workingcopy.application.LiveRowLock;
import eu.uniche.iguide.workingcopy.application.WorkingCopyHandler;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The working copy of an ACTIVE Route: the Route's update body without {@code version}, plus its ordered
 * {@code stops}, its {@code categoryIds} (none or one), and its non-INLINE {@code media} in order
 * (DECISIONS.md D-027).
 */
@Component
public class RouteWorkingCopyHandler implements WorkingCopyHandler<RouteWorkingCopyHandler.State> {

    public record State(String slug, RouteType type, TravelMode travelMode, RouteDifficulty difficulty,
                        Integer estimatedDurationMinutes, Integer distanceMeters, boolean loop, String internalName,
                        boolean featured, Map<String, RouteTranslationBody> translations, List<Stop> stops,
                        List<UUID> categoryIds, List<ResourceAssetUseCase.Media> media) {
    }

    /** {@code id} keeps an existing stop; {@code null} or an unknown id makes a new one. */
    public record Stop(UUID id, UUID poiId, Map<String, StopTranslationBody> translations) {
    }

    private final LiveRowLock lock;
    private final RouteRules rules;
    private final UpdateRouteUseCase updateRoute;
    private final RouteStopUseCase stops;
    private final ResourceCategoryUseCase categories;
    private final ResourceAssetUseCase media;

    public RouteWorkingCopyHandler(LiveRowLock lock, RouteRules rules, UpdateRouteUseCase updateRoute,
                                   RouteStopUseCase stops, ResourceCategoryUseCase categories,
                                   ResourceAssetUseCase media) {
        this.lock = lock;
        this.rules = rules;
        this.updateRoute = updateRoute;
        this.stops = stops;
        this.categories = categories;
        this.media = media;
    }

    @Override
    public WorkingCopyResourceType type() {
        return WorkingCopyResourceType.ROUTE;
    }

    @Override
    public Class<State> stateType() {
        return State.class;
    }

    @Override
    public LiveResource lock(UUID projectId, UUID resourceId) {
        Route route = lock.lock(Route.class, projectId, resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("No such route in this project."));
        return new LiveResource(route.getStatus(), route.getVersion());
    }

    @Override
    public void write(UUID projectId, UUID resourceId, UnichePrincipal principal, State state) {
        updateRoute.write(projectId, rules.requireRoute(projectId, resourceId), principal, new UpdateRouteCommand(
                null, state.slug(), state.type(), state.travelMode(), state.difficulty(),
                state.estimatedDurationMinutes(), state.distanceMeters(), state.loop(), state.internalName(),
                state.featured(), RouteRequests.routeInputs(state.translations())));
        List<Stop> wanted = state.stops() == null ? List.of() : state.stops();
        stops.replaceStops(projectId, resourceId, wanted.stream()
                .map(s -> new RouteStopUseCase.WantedStop(s.id(), s.poiId(), RouteRequests.stopInputs(s.translations())))
                .toList());
        categories.replace(projectId, CategoryScope.ROUTE, resourceId, state.categoryIds());
        media.replace(projectId, AssetResourceType.ROUTE, resourceId, state.media());
    }

    @Override
    public Object render(UUID projectId, UUID resourceId, Instant workingCopyUpdatedAt) {
        return RouteResponse.from(rules.view(rules.requireRoute(projectId, resourceId)), workingCopyUpdatedAt);
    }
}
