package eu.uniche.iguide.floorplan.application;

import eu.uniche.iguide.floorplan.domain.FloorPlan;
import eu.uniche.iguide.floorplan.domain.FloorPlanPosition;
import eu.uniche.iguide.floorplan.infrastructure.FloorPlanPositionRepository;
import eu.uniche.iguide.floorplan.infrastructure.FloorPlanRepository;
import eu.uniche.iguide.readiness.application.ReadinessCheck;
import eu.uniche.iguide.readiness.application.ReadinessContext;
import eu.uniche.iguide.readiness.application.ReadinessReport;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Readiness of ACTIVE floor plans (DECISIONS.md D-033): a positioned Resource that is gone or archived. */
@Component
public class FloorPlanReadiness implements ReadinessCheck {

    private final FloorPlanRepository plans;
    private final FloorPlanPositionRepository positions;

    public FloorPlanReadiness(FloorPlanRepository plans, FloorPlanPositionRepository positions) {
        this.plans = plans;
        this.positions = positions;
    }

    @Override
    public void check(ReadinessContext context, ReadinessReport report) {
        List<FloorPlan> active = plans.findByProjectId(context.projectId()).stream()
                .filter(p -> p.getStatus() == ResourceStatus.ACTIVE).toList();
        if (active.isEmpty()) {
            return;
        }
        Map<UUID, List<FloorPlanPosition>> byPlan = positions.findByProjectIdAndFloorPlanIdInOrderByIdAsc(
                        context.projectId(), active.stream().map(FloorPlan::getId).toList()).stream()
                .collect(Collectors.groupingBy(FloorPlanPosition::getFloorPlanId));
        for (FloorPlan plan : active) {
            List<FloorPlanPosition> own = byPlan.getOrDefault(plan.getId(), List.of());
            for (int i = 0; i < own.size(); i++) {
                if (context.index().dangling(own.get(i).getResourceType(), own.get(i).getResourceId())) {
                    report.block("FLOOR_PLAN", plan.getId(), plan.getInternalName(), null, "positions[" + i + "]",
                            "DANGLING_REFERENCE");
                }
            }
        }
    }
}
