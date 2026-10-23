package eu.uniche.iguide.map.application;

import eu.uniche.iguide.map.domain.GeographicMap;
import eu.uniche.iguide.map.domain.GeographicMapResource;
import eu.uniche.iguide.map.infrastructure.GeographicMapResourceRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.DuplicateValueException;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ErrorPaths;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.spatial.application.SpatialRules;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Places, moves, and removes Resources on a DRAFT map ({@code 07} section 14,
 * {@code .../maps/{id}/resources}). An ACTIVE map changes its placements through its working copy, whose apply
 * calls {@link #replacePlacements} (DECISIONS.md D-027); these endpoints refuse it.
 */
@Service
public class MapPlacementUseCase {

    private final ProjectAccessGateway accessGateway;
    private final MapRules rules;
    private final SpatialRules spatial;
    private final GeographicMapResourceRepository placements;
    private final TransactionTemplate transactionTemplate;

    public MapPlacementUseCase(ProjectAccessGateway accessGateway, MapRules rules, SpatialRules spatial,
                               GeographicMapResourceRepository placements, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.spatial = spatial;
        this.placements = placements;
        this.transactionTemplate = transactionTemplate;
    }

    /** One wanted placement of a working-copy payload; {@code id} names an existing placement to keep. */
    public record Wanted(UUID id, String resourceType, UUID resourceId, BigDecimal latitude, BigDecimal longitude) {
    }

    public GeographicMapResource place(UUID projectId, UUID mapId, UnichePrincipal principal, String resourceType,
                                       UUID resourceId, BigDecimal latitude, BigDecimal longitude) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            requireDraftMap(projectId, mapId);
            return placeIn(projectId, mapId, resourceType, resourceId, latitude, longitude);
        });
    }

    public GeographicMapResource move(UUID projectId, UUID mapId, UUID placementId, UnichePrincipal principal,
                                      BigDecimal latitude, BigDecimal longitude) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            requireDraftMap(projectId, mapId);
            return moveIn(requirePlacement(projectId, mapId, placementId), latitude, longitude);
        });
    }

    public void remove(UUID projectId, UUID mapId, UUID placementId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        transactionTemplate.executeWithoutResult(status -> {
            requireDraftMap(projectId, mapId);
            placements.delete(requirePlacement(projectId, mapId, placementId));
        });
    }

    /**
     * Makes the map's placements exactly {@code wanted}, inside the caller's transaction and without a status
     * check: the apply of a working copy (DECISIONS.md D-027). A wanted entry keeps the placement its {@code id}
     * names, or else one of the same Resource, and moves it; the others are placed with the rules of
     * {@link #place}; placements no entry claims are removed. Errors are reported at {@code placements[i]}.
     */
    public List<GeographicMapResource> replacePlacements(UUID projectId, UUID mapId, List<Wanted> wanted) {
        List<Wanted> desired = wanted == null ? List.of() : wanted;
        List<GeographicMapResource> unclaimed = new ArrayList<>(
                placements.findByProjectIdAndMapIdOrderByIdAsc(projectId, mapId));
        GeographicMapResource[] kept = new GeographicMapResource[desired.size()];
        Set<String> targets = new HashSet<>();
        for (int i = 0; i < desired.size(); i++) {
            Wanted w = desired.get(i);
            if (!targets.add(w.resourceType() + ":" + w.resourceId())) {
                throw new DuplicateValueException("placements[" + i + "].resourceId",
                        "The resource is already placed on this map.");
            }
            GeographicMapResource match = unclaimed.stream()
                    .filter(p -> w.id() != null && p.getId().equals(w.id()) && sameTarget(p, w))
                    .findFirst()
                    .or(() -> unclaimed.stream().filter(p -> sameTarget(p, w)).findFirst())
                    .orElse(null);
            if (match != null) {
                unclaimed.remove(match);
                kept[i] = match;
            }
        }
        placements.deleteAll(unclaimed);
        placements.flush();
        List<GeographicMapResource> result = new ArrayList<>();
        for (int i = 0; i < desired.size(); i++) {
            Wanted w = desired.get(i);
            GeographicMapResource existing = kept[i];
            result.add(ErrorPaths.within("placements[" + i + "]", () -> existing != null
                    ? moveIn(existing, w.latitude(), w.longitude())
                    : placeIn(projectId, mapId, w.resourceType(), w.resourceId(), w.latitude(), w.longitude())));
        }
        return result;
    }

    private GeographicMapResource placeIn(UUID projectId, UUID mapId, String resourceType, UUID resourceId,
                                          BigDecimal latitude, BigDecimal longitude) {
        List<ErrorItem> errors = new ArrayList<>();
        spatial.checkTarget(projectId, resourceType, resourceId, errors);
        ResourceRules.checkLatitude(latitude, "latitude", errors);
        ResourceRules.checkLongitude(longitude, "longitude", errors);
        ResourceRules.throwIfAny(errors, "The placement is invalid.");
        if (placements.existsByProjectIdAndMapIdAndResourceTypeAndResourceId(projectId, mapId, resourceType,
                resourceId)) {
            throw new DuplicateValueException("resourceId", "The resource is already placed on this map.");
        }
        return placements.saveAndFlush(GeographicMapResource.of(projectId, mapId, resourceType, resourceId,
                latitude, longitude));
    }

    private GeographicMapResource moveIn(GeographicMapResource placement, BigDecimal latitude, BigDecimal longitude) {
        List<ErrorItem> errors = new ArrayList<>();
        ResourceRules.checkLatitude(latitude, "latitude", errors);
        ResourceRules.checkLongitude(longitude, "longitude", errors);
        ResourceRules.throwIfAny(errors, "The placement is invalid.");
        placement.moveTo(latitude, longitude);
        return placements.saveAndFlush(placement);
    }

    private static boolean sameTarget(GeographicMapResource placement, Wanted wanted) {
        return Objects.equals(placement.getResourceType(), wanted.resourceType())
                && Objects.equals(placement.getResourceId(), wanted.resourceId());
    }

    private void requireDraftMap(UUID projectId, UUID mapId) {
        GeographicMap map = rules.requireMap(projectId, mapId);
        ResourceRules.requireDraft(map.getStatus(), "map");
    }

    private GeographicMapResource requirePlacement(UUID projectId, UUID mapId, UUID placementId) {
        return SpatialRules.require(placements.findByProjectIdAndMapIdAndId(projectId, mapId, placementId),
                "placement on this map");
    }
}
