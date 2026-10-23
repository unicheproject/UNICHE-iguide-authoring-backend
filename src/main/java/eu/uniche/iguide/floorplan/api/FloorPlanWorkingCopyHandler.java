package eu.uniche.iguide.floorplan.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import eu.uniche.iguide.floorplan.application.FloorPlanPositionUseCase;
import eu.uniche.iguide.floorplan.application.FloorPlanRules;
import eu.uniche.iguide.floorplan.application.UpdateFloorPlanUseCase;
import eu.uniche.iguide.floorplan.domain.FloorPlan;
import eu.uniche.iguide.floorplan.domain.FloorPlan.Details;
import eu.uniche.iguide.floorplan.infrastructure.FloorPlanPositionRepository;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.workingcopy.application.LiveResource;
import eu.uniche.iguide.workingcopy.application.LiveRowLock;
import eu.uniche.iguide.workingcopy.application.WorkingCopyHandler;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The working copy of an ACTIVE Floor Plan: the plan's update body without {@code version}, plus its positions,
 * each with the {@code id} of the position it keeps when it has one (DECISIONS.md D-027). The rendered plan
 * carries its positions, since the plan's own read leaves them to {@code .../positions}.
 */
@Component
public class FloorPlanWorkingCopyHandler implements WorkingCopyHandler<FloorPlanWorkingCopyHandler.State> {

    public record State(String internalName, UUID placeId, UUID assetId, Integer width, Integer height,
                        List<FloorPlanPositionUseCase.Wanted> positions) {
    }

    /** A plan read with its positions: the fields of {@link FloorPlanResponse} and {@code positions}. */
    public record FloorPlanWithPositions(@JsonUnwrapped FloorPlanResponse plan,
                                         List<FloorPlanResponse.Position> positions) {
    }

    private final LiveRowLock lock;
    private final FloorPlanRules rules;
    private final UpdateFloorPlanUseCase updatePlan;
    private final FloorPlanPositionUseCase positionUseCase;
    private final FloorPlanPositionRepository positions;

    public FloorPlanWorkingCopyHandler(LiveRowLock lock, FloorPlanRules rules, UpdateFloorPlanUseCase updatePlan,
                                       FloorPlanPositionUseCase positionUseCase, FloorPlanPositionRepository positions) {
        this.lock = lock;
        this.rules = rules;
        this.updatePlan = updatePlan;
        this.positionUseCase = positionUseCase;
        this.positions = positions;
    }

    @Override
    public WorkingCopyResourceType type() {
        return WorkingCopyResourceType.FLOOR_PLAN;
    }

    @Override
    public Class<State> stateType() {
        return State.class;
    }

    @Override
    public LiveResource lock(UUID projectId, UUID resourceId) {
        FloorPlan plan = lock.lock(FloorPlan.class, projectId, resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("No such floor plan in this project."));
        return new LiveResource(plan.getStatus(), plan.getVersion());
    }

    @Override
    public void write(UUID projectId, UUID resourceId, UnichePrincipal principal, State state) {
        updatePlan.write(projectId, rules.requirePlan(projectId, resourceId), principal, new Details(
                state.internalName(), state.placeId(), state.assetId(), state.width(), state.height()));
        positionUseCase.replacePositions(projectId, resourceId, state.positions());
    }

    @Override
    public Object render(UUID projectId, UUID resourceId, Instant workingCopyUpdatedAt) {
        return new FloorPlanWithPositions(
                FloorPlanResponse.from(rules.requirePlan(projectId, resourceId), workingCopyUpdatedAt),
                positions.findByProjectIdAndFloorPlanIdOrderByIdAsc(projectId, resourceId).stream()
                        .map(FloorPlanResponse.Position::from).toList());
    }
}
