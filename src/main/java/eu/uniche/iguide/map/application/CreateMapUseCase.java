package eu.uniche.iguide.map.application;

import eu.uniche.iguide.map.domain.GeographicMap;
import eu.uniche.iguide.map.domain.GeographicMap.MapView;
import eu.uniche.iguide.map.infrastructure.GeographicMapRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ResourceRules;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates a DRAFT map with every type visible and the visitor filter on ({@code 03} section 7).
 * {@code area} and {@code osmId} record what the author searched for in the CMS.
 */
@Service
public class CreateMapUseCase {

    private final ProjectAccessGateway accessGateway;
    private final MapRules rules;
    private final GeographicMapRepository maps;
    private final TransactionTemplate transactionTemplate;

    public CreateMapUseCase(ProjectAccessGateway accessGateway, MapRules rules, GeographicMapRepository maps,
                            TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.maps = maps;
        this.transactionTemplate = transactionTemplate;
    }

    public GeographicMap execute(UUID projectId, UnichePrincipal principal, MapView view, String area, String osmId) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            List<ErrorItem> errors = new ArrayList<>();
            MapView checked = rules.checkView(projectId, view, null, errors);
            ResourceRules.throwIfAny(errors, "The map is invalid.");
            Map<String, Object> configuration = new HashMap<>();
            if (ResourceRules.trimToNull(area) != null) {
                configuration.put("area", area.strip());
            }
            if (ResourceRules.trimToNull(osmId) != null) {
                configuration.put("osmId", osmId.strip());
            }
            return maps.saveAndFlush(GeographicMap.draft(projectId, checked, configuration, principal.subject(),
                    Instant.now()));
        });
    }
}
