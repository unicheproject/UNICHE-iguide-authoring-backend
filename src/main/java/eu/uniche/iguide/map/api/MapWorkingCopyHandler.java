package eu.uniche.iguide.map.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import eu.uniche.iguide.map.application.MapPlacementUseCase;
import eu.uniche.iguide.map.application.MapRules;
import eu.uniche.iguide.map.application.UpdateMapUseCase;
import eu.uniche.iguide.map.domain.GeographicMap;
import eu.uniche.iguide.map.domain.GeographicMap.MapView;
import eu.uniche.iguide.map.domain.MapVisibility;
import eu.uniche.iguide.map.infrastructure.GeographicMapResourceRepository;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.workingcopy.application.LiveResource;
import eu.uniche.iguide.workingcopy.application.LiveRowLock;
import eu.uniche.iguide.workingcopy.application.WorkingCopyHandler;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The working copy of an ACTIVE map: the map's update body without {@code version}, plus its placements, each
 * with the {@code id} of the placement it keeps when it has one (DECISIONS.md D-027). The rendered map carries
 * its placements, since the map's own read leaves them to {@code .../resources}.
 */
@Component
public class MapWorkingCopyHandler implements WorkingCopyHandler<MapWorkingCopyHandler.State> {

    public record State(String internalName, UUID placeId, BigDecimal initialLatitude, BigDecimal initialLongitude,
                        BigDecimal initialZoom, BigDecimal minLatitude, BigDecimal minLongitude,
                        BigDecimal maxLatitude, BigDecimal maxLongitude, MapVisibility visibility,
                        boolean visitorFilterEnabled, List<MapPlacementUseCase.Wanted> placements) {
    }

    /** A map read with its placements: the fields of {@link MapResponse} and {@code placements}. */
    public record MapWithPlacements(@JsonUnwrapped MapResponse map, List<MapResponse.Placement> placements) {
    }

    private final LiveRowLock lock;
    private final MapRules rules;
    private final UpdateMapUseCase updateMap;
    private final MapPlacementUseCase placementUseCase;
    private final GeographicMapResourceRepository placements;

    public MapWorkingCopyHandler(LiveRowLock lock, MapRules rules, UpdateMapUseCase updateMap,
                                 MapPlacementUseCase placementUseCase, GeographicMapResourceRepository placements) {
        this.lock = lock;
        this.rules = rules;
        this.updateMap = updateMap;
        this.placementUseCase = placementUseCase;
        this.placements = placements;
    }

    @Override
    public WorkingCopyResourceType type() {
        return WorkingCopyResourceType.GEOGRAPHIC_MAP;
    }

    @Override
    public Class<State> stateType() {
        return State.class;
    }

    @Override
    public LiveResource lock(UUID projectId, UUID resourceId) {
        GeographicMap map = lock.lock(GeographicMap.class, projectId, resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("No such map in this project."));
        return new LiveResource(map.getStatus(), map.getVersion());
    }

    @Override
    public void write(UUID projectId, UUID resourceId, UnichePrincipal principal, State state) {
        MapView view = new MapView(state.internalName(), state.placeId(), state.initialLatitude(),
                state.initialLongitude(), state.initialZoom(), state.minLatitude(), state.minLongitude(),
                state.maxLatitude(), state.maxLongitude());
        updateMap.write(projectId, rules.requireMap(projectId, resourceId), principal, view, state.visibility(),
                state.visitorFilterEnabled());
        placementUseCase.replacePlacements(projectId, resourceId, state.placements());
    }

    @Override
    public Object render(UUID projectId, UUID resourceId, Instant workingCopyUpdatedAt) {
        return new MapWithPlacements(MapResponse.from(rules.requireMap(projectId, resourceId), workingCopyUpdatedAt),
                placements.findByProjectIdAndMapIdOrderByIdAsc(projectId, resourceId).stream()
                        .map(MapResponse.Placement::from).toList());
    }
}
