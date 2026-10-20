package eu.uniche.iguide.floorplan.application;

import eu.uniche.iguide.floorplan.domain.FloorPlan;
import eu.uniche.iguide.floorplan.domain.FloorPlanPosition;
import eu.uniche.iguide.floorplan.infrastructure.FloorPlanPositionRepository;
import eu.uniche.iguide.floorplan.infrastructure.FloorPlanRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.resource.application.PageRequests;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/** Reads Floor Plans, their positions, and their references. */
@Service
public class GetFloorPlanQuery {

    private final ProjectAccessGateway accessGateway;
    private final FloorPlanRules rules;
    private final FloorPlanRepository plans;
    private final FloorPlanPositionRepository positions;

    public GetFloorPlanQuery(ProjectAccessGateway accessGateway, FloorPlanRules rules, FloorPlanRepository plans,
                             FloorPlanPositionRepository positions) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.plans = plans;
        this.positions = positions;
    }

    /** In {@code sortOrder}, optionally only one Place's plans, which is its floor switcher order. */
    public Page<FloorPlan> list(UUID projectId, UnichePrincipal principal, ResourceStatus status, UUID placeId,
                                Integer page, Integer size) {
        accessGateway.requireAccess(projectId, principal);
        Specification<FloorPlan> spec = (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (placeId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("placeId"), placeId));
        }
        return plans.findAll(spec, PageRequests.of(page, size, Sort.by("sortOrder", "createdAt")));
    }

    public FloorPlan get(UUID projectId, UUID planId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return rules.requirePlan(projectId, planId);
    }

    public List<FloorPlanPosition> positions(UUID projectId, UUID planId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        rules.requirePlan(projectId, planId);
        return positions.findByProjectIdAndFloorPlanIdOrderByIdAsc(projectId, planId);
    }

    /** Nothing refers to a Floor Plan until Pages carry FLOOR_PLAN components (Milestone 6). */
    public List<ResourceReference> references(UUID projectId, UUID planId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        rules.requirePlan(projectId, planId);
        return List.of();
    }
}
