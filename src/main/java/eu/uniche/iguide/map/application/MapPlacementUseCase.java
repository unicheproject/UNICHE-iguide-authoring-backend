package eu.uniche.iguide.map.application;

import eu.uniche.iguide.map.domain.GeographicMap;
import eu.uniche.iguide.map.domain.GeographicMapResource;
import eu.uniche.iguide.map.infrastructure.GeographicMapResourceRepository;
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
 * Places, moves, and removes Resources on a map ({@code 07} section 14, {@code .../maps/{id}/resources}). On a
 * DRAFT map the rows are written; on an ACTIVE one the change goes into the map's working copy, started from the
 * live map when it has none, and visitors see it after apply (DECISIONS.md D-044). The answer has the same shape
 * either way. An ARCHIVED map is refused.
 */
@Service
public class MapPlacementUseCase {

    private static final String PLACEMENTS = "placements";

    private final ProjectAccessGateway accessGateway;
    private final MapRules rules;
    private final MapPlacementWriter writer;
    private final GeographicMapResourceRepository placements;
    private final PatchWorkingCopyUseCase patch;
    private final TransactionTemplate transactionTemplate;

    public MapPlacementUseCase(ProjectAccessGateway accessGateway, MapRules rules, MapPlacementWriter writer,
                               GeographicMapResourceRepository placements, PatchWorkingCopyUseCase patch,
                               TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.writer = writer;
        this.placements = placements;
        this.patch = patch;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * One placement: a wanted one of a working-copy payload, where {@code id} names an existing placement to keep,
     * and what the endpoints answer. A placement added to a working copy has an id there until apply gives it a
     * row of its own.
     */
    public record Wanted(UUID id, String resourceType, UUID resourceId, BigDecimal latitude, BigDecimal longitude) {

        static Wanted of(GeographicMapResource r) {
            return new Wanted(r.getId(), r.getResourceType(), r.getResourceId(), r.getLatitude(), r.getLongitude());
        }
    }

    public Wanted place(UUID projectId, UUID mapId, UnichePrincipal principal, String resourceType,
                        UUID resourceId, BigDecimal latitude, BigDecimal longitude) {
        accessGateway.requireAccess(projectId, principal);
        Wanted placed = transactionTemplate.execute(status -> active(projectId, mapId) ? null
                : Wanted.of(writer.place(projectId, mapId, resourceType, resourceId, latitude, longitude)));
        if (placed != null) {
            return placed;
        }
        writer.checkNew(projectId, resourceType, resourceId, latitude, longitude);
        return patch.execute(projectId, WorkingCopyResourceType.GEOGRAPHIC_MAP, mapId, principal, payload -> {
            List<Map<String, Object>> items = PatchWorkingCopyUseCase.children(payload, PLACEMENTS);
            if (items.stream().anyMatch(item -> Objects.equals(String.valueOf(item.get("resourceType")), resourceType)
                    && Objects.equals(String.valueOf(item.get("resourceId")), String.valueOf(resourceId)))) {
                throw new DuplicateValueException("resourceId", MapPlacementWriter.DUPLICATE);
            }
            Wanted added = new Wanted(UUID.randomUUID(), resourceType, resourceId, latitude, longitude);
            items.add(entry(added));
            return new Patched<>(added, PLACEMENTS + "[" + (items.size() - 1) + "]");
        });
    }

    public Wanted move(UUID projectId, UUID mapId, UUID placementId, UnichePrincipal principal,
                       BigDecimal latitude, BigDecimal longitude) {
        accessGateway.requireAccess(projectId, principal);
        Wanted moved = transactionTemplate.execute(status -> active(projectId, mapId) ? null
                : Wanted.of(writer.move(requirePlacement(projectId, mapId, placementId), latitude, longitude)));
        if (moved != null) {
            return moved;
        }
        MapPlacementWriter.checkMove(latitude, longitude);
        return patch.execute(projectId, WorkingCopyResourceType.GEOGRAPHIC_MAP, mapId, principal, payload -> {
            List<Map<String, Object>> items = PatchWorkingCopyUseCase.children(payload, PLACEMENTS);
            Map<String, Object> item = PatchWorkingCopyUseCase.child(items, placementId)
                    .orElseThrow(() -> SpatialRules.notFound("placement on this map"));
            item.put("latitude", latitude);
            item.put("longitude", longitude);
            return new Patched<>(new Wanted(placementId, String.valueOf(item.get("resourceType")),
                    UUID.fromString(String.valueOf(item.get("resourceId"))), latitude, longitude),
                    PLACEMENTS + "[" + items.indexOf(item) + "]");
        });
    }

    public void remove(UUID projectId, UUID mapId, UUID placementId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        boolean removed = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            if (active(projectId, mapId)) {
                return false;
            }
            placements.delete(requirePlacement(projectId, mapId, placementId));
            return true;
        }));
        if (!removed) {
            patch.execute(projectId, WorkingCopyResourceType.GEOGRAPHIC_MAP, mapId, principal, payload -> {
                List<Map<String, Object>> items = PatchWorkingCopyUseCase.children(payload, PLACEMENTS);
                items.remove(PatchWorkingCopyUseCase.child(items, placementId)
                        .orElseThrow(() -> SpatialRules.notFound("placement on this map")));
                return new Patched<>(true, PLACEMENTS);
            });
        }
    }

    /** Whether the map is ACTIVE; a DRAFT one is written directly, an ARCHIVED one refused. */
    private boolean active(UUID projectId, UUID mapId) {
        GeographicMap map = rules.requireMap(projectId, mapId);
        if (map.getStatus() == ResourceStatus.ACTIVE) {
            return true;
        }
        ResourceRules.requireDraft(map.getStatus(), "map");
        return false;
    }

    private static Map<String, Object> entry(Wanted w) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", w.id());
        item.put("resourceType", w.resourceType());
        item.put("resourceId", w.resourceId());
        item.put("latitude", w.latitude());
        item.put("longitude", w.longitude());
        return item;
    }

    private GeographicMapResource requirePlacement(UUID projectId, UUID mapId, UUID placementId) {
        return SpatialRules.require(placements.findByProjectIdAndMapIdAndId(projectId, mapId, placementId),
                "placement on this map");
    }
}
