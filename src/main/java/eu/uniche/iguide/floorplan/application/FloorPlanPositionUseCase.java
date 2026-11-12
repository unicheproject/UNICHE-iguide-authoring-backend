package eu.uniche.iguide.floorplan.application;

import eu.uniche.iguide.floorplan.domain.FloorPlan;
import eu.uniche.iguide.floorplan.domain.FloorPlanPosition;
import eu.uniche.iguide.floorplan.infrastructure.FloorPlanPositionRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.DuplicateValueException;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.spatial.application.SpatialRules;
import eu.uniche.iguide.workingcopy.application.PatchWorkingCopyUseCase;
import eu.uniche.iguide.workingcopy.application.PatchWorkingCopyUseCase.Patched;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Positions, moves, and removes Resources on a Floor Plan ({@code 07} section 14,
 * {@code .../floor-plans/{id}/positions}). On a DRAFT plan the rows are written; on an ACTIVE one the change goes
 * into the plan's working copy, started from the live plan when it has none, and visitors see it after apply
 * (DECISIONS.md D-044). The answer has the same shape either way. An ARCHIVED plan is refused.
 */
@Service
public class FloorPlanPositionUseCase {

    private static final String POSITIONS = "positions";

    private final ProjectAccessGateway accessGateway;
    private final FloorPlanRules rules;
    private final FloorPlanPositionWriter writer;
    private final FloorPlanPositionRepository positions;
    private final PatchWorkingCopyUseCase patch;
    private final TransactionTemplate transactionTemplate;

    public FloorPlanPositionUseCase(ProjectAccessGateway accessGateway, FloorPlanRules rules,
                                    FloorPlanPositionWriter writer, FloorPlanPositionRepository positions,
                                    PatchWorkingCopyUseCase patch, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.writer = writer;
        this.positions = positions;
        this.patch = patch;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * One position: a wanted one of a working-copy payload, where {@code id} names an existing position to keep,
     * and what the endpoints answer. A position added to a working copy has an id there until apply gives it a row
     * of its own.
     */
    public record Wanted(UUID id, String resourceType, UUID resourceId, BigDecimal x, BigDecimal y, Integer rotation) {

        static Wanted of(FloorPlanPosition p) {
            return new Wanted(p.getId(), p.getResourceType(), p.getResourceId(), p.getX(), p.getY(), p.getRotation());
        }
    }

    public Wanted position(UUID projectId, UUID planId, UnichePrincipal principal, String resourceType,
                           UUID resourceId, BigDecimal x, BigDecimal y, Integer rotation) {
        accessGateway.requireAccess(projectId, principal);
        Wanted placed = transactionTemplate.execute(status -> active(projectId, planId) ? null
                : Wanted.of(writer.position(projectId, planId, resourceType, resourceId, x, y, rotation)));
        if (placed != null) {
            return placed;
        }
        writer.checkNew(projectId, resourceType, resourceId, x, y, rotation);
        return patch.execute(projectId, WorkingCopyResourceType.FLOOR_PLAN, planId, principal, payload -> {
            List<Map<String, Object>> items = PatchWorkingCopyUseCase.children(payload, POSITIONS);
            if (items.stream().anyMatch(item -> Objects.equals(String.valueOf(item.get("resourceType")), resourceType)
                    && Objects.equals(String.valueOf(item.get("resourceId")), String.valueOf(resourceId)))) {
                throw new DuplicateValueException("resourceId", FloorPlanPositionWriter.DUPLICATE);
            }
            Wanted added = new Wanted(UUID.randomUUID(), resourceType, resourceId, x, y, rotation);
            items.add(entry(added));
            return new Patched<>(added, POSITIONS + "[" + (items.size() - 1) + "]");
        });
    }

    public Wanted move(UUID projectId, UUID planId, UUID positionId, UnichePrincipal principal,
                       BigDecimal x, BigDecimal y, Integer rotation) {
        accessGateway.requireAccess(projectId, principal);
        Wanted moved = transactionTemplate.execute(status -> active(projectId, planId) ? null
                : Wanted.of(writer.move(requirePosition(projectId, planId, positionId), x, y, rotation)));
        if (moved != null) {
            return moved;
        }
        FloorPlanPositionWriter.checkMove(x, y, rotation);
        return patch.execute(projectId, WorkingCopyResourceType.FLOOR_PLAN, planId, principal, payload -> {
            List<Map<String, Object>> items = PatchWorkingCopyUseCase.children(payload, POSITIONS);
            Map<String, Object> item = PatchWorkingCopyUseCase.child(items, positionId)
                    .orElseThrow(() -> SpatialRules.notFound("position on this floor plan"));
            item.put("x", x);
            item.put("y", y);
            item.put("rotation", rotation);
            return new Patched<>(new Wanted(positionId, String.valueOf(item.get("resourceType")),
                    UUID.fromString(String.valueOf(item.get("resourceId"))), x, y, rotation),
                    POSITIONS + "[" + items.indexOf(item) + "]");
        });
    }

    public void remove(UUID projectId, UUID planId, UUID positionId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        boolean removed = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            if (active(projectId, planId)) {
                return false;
            }
            positions.delete(requirePosition(projectId, planId, positionId));
            return true;
        }));
        if (!removed) {
            patch.execute(projectId, WorkingCopyResourceType.FLOOR_PLAN, planId, principal, payload -> {
                List<Map<String, Object>> items = PatchWorkingCopyUseCase.children(payload, POSITIONS);
                items.remove(PatchWorkingCopyUseCase.child(items, positionId)
                        .orElseThrow(() -> SpatialRules.notFound("position on this floor plan")));
                return new Patched<>(true, POSITIONS);
            });
        }
    }

    /** Whether the plan is ACTIVE; a DRAFT one is written directly, an ARCHIVED one refused. */
    private boolean active(UUID projectId, UUID planId) {
        FloorPlan plan = rules.requirePlan(projectId, planId);
        if (plan.getStatus() == ResourceStatus.ACTIVE) {
            return true;
        }
        ResourceRules.requireDraft(plan.getStatus(), "floor plan");
        return false;
    }

    private static Map<String, Object> entry(Wanted w) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", w.id());
        item.put("resourceType", w.resourceType());
        item.put("resourceId", w.resourceId());
        item.put("x", w.x());
        item.put("y", w.y());
        item.put("rotation", w.rotation());
        return item;
    }

    private FloorPlanPosition requirePosition(UUID projectId, UUID planId, UUID positionId) {
        return SpatialRules.require(positions.findByProjectIdAndFloorPlanIdAndId(projectId, planId, positionId),
                "position on this floor plan");
    }
}
