package eu.uniche.iguide.map.application;

import eu.uniche.iguide.map.domain.GeographicMap;
import eu.uniche.iguide.map.domain.GeographicMapResource;
import eu.uniche.iguide.map.infrastructure.GeographicMapRepository;
import eu.uniche.iguide.map.infrastructure.GeographicMapResourceRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.resource.application.PageRequests;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** Reads maps, their placements, and their references. */
@Service
public class GetMapQuery {

    private final ProjectAccessGateway accessGateway;
    private final MapRules rules;
    private final GeographicMapRepository maps;
    private final GeographicMapResourceRepository placements;

    public GetMapQuery(ProjectAccessGateway accessGateway, MapRules rules, GeographicMapRepository maps,
                       GeographicMapResourceRepository placements) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.maps = maps;
        this.placements = placements;
    }

    /** Maps have no {@code sortOrder}, so they list most recently updated first ({@code 06} section 16). */
    public Page<GeographicMap> list(UUID projectId, UnichePrincipal principal, ResourceStatus status, Integer page,
                                    Integer size) {
        accessGateway.requireAccess(projectId, principal);
        var pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return status == null ? maps.findByProjectId(projectId, pageable)
                : maps.findByProjectIdAndStatus(projectId, status, pageable);
    }

    public GeographicMap get(UUID projectId, UUID mapId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return rules.requireMap(projectId, mapId);
    }

    public List<GeographicMapResource> placements(UUID projectId, UUID mapId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        rules.requireMap(projectId, mapId);
        return placements.findByProjectIdAndMapIdOrderByIdAsc(projectId, mapId);
    }

    /** Nothing refers to a map until Pages carry MAP components (Milestone 6). */
    public List<ResourceReference> references(UUID projectId, UUID mapId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        rules.requireMap(projectId, mapId);
        return List.of();
    }
}
