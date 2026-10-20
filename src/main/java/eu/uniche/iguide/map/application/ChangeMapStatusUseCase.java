package eu.uniche.iguide.map.application;

import eu.uniche.iguide.map.domain.GeographicMap;
import eu.uniche.iguide.map.infrastructure.GeographicMapRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies one lifecycle transition. A map has no required fields ({@code 03} section 1) and nothing
 * refers to it yet, so only the transition table applies. Archival frees the linked Place.
 */
@Service
public class ChangeMapStatusUseCase {

    private final ProjectAccessGateway accessGateway;
    private final MapRules rules;
    private final GeographicMapRepository maps;
    private final TransactionTemplate transactionTemplate;

    public ChangeMapStatusUseCase(ProjectAccessGateway accessGateway, MapRules rules, GeographicMapRepository maps,
                                  TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.maps = maps;
        this.transactionTemplate = transactionTemplate;
    }

    public GeographicMap execute(UUID projectId, UUID mapId, UnichePrincipal principal, ResourceStatus target,
                                 Long version) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            GeographicMap map = rules.requireMap(projectId, mapId);
            ResourceRules.requireCurrentVersion(map.getVersion(), version, "map");
            ResourceRules.requireTransition(map.getStatus(), target, "map");
            map.changeStatus(target, principal.subject(), Instant.now());
            return maps.saveAndFlush(map);
        });
    }
}
