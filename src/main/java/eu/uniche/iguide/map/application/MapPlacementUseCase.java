package eu.uniche.iguide.map.application;

import eu.uniche.iguide.map.domain.GeographicMap;
import eu.uniche.iguide.map.domain.GeographicMapResource;
import eu.uniche.iguide.map.infrastructure.GeographicMapResourceRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.DuplicateValueException;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.spatial.application.SpatialRules;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Places, moves, and removes Resources on a DRAFT map ({@code 07} section 14,
 * {@code .../maps/{id}/resources}). On an ACTIVE map these writes belong in its working copy, which does
 * not exist yet, so they are refused.
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

    public GeographicMapResource place(UUID projectId, UUID mapId, UnichePrincipal principal, String resourceType,
                                       UUID resourceId, BigDecimal latitude, BigDecimal longitude) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            requireDraftMap(projectId, mapId);
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
        });
    }

    public GeographicMapResource move(UUID projectId, UUID mapId, UUID placementId, UnichePrincipal principal,
                                      BigDecimal latitude, BigDecimal longitude) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            requireDraftMap(projectId, mapId);
            GeographicMapResource placement = requirePlacement(projectId, mapId, placementId);
            List<ErrorItem> errors = new ArrayList<>();
            ResourceRules.checkLatitude(latitude, "latitude", errors);
            ResourceRules.checkLongitude(longitude, "longitude", errors);
            ResourceRules.throwIfAny(errors, "The placement is invalid.");
            placement.moveTo(latitude, longitude);
            return placements.saveAndFlush(placement);
        });
    }

    public void remove(UUID projectId, UUID mapId, UUID placementId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        transactionTemplate.executeWithoutResult(status -> {
            requireDraftMap(projectId, mapId);
            placements.delete(requirePlacement(projectId, mapId, placementId));
        });
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
