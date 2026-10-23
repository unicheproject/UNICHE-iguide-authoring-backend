package eu.uniche.iguide.map.application;

import eu.uniche.iguide.map.domain.GeographicMap;
import eu.uniche.iguide.map.domain.GeographicMap.MapView;
import eu.uniche.iguide.map.domain.MapVisibility;
import eu.uniche.iguide.map.infrastructure.GeographicMapRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ResourceRules;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Saves a DRAFT map: its view, bounds, Place link, visibility, and visitor filter. An ACTIVE map is edited
 * through its working copy, whose save and apply reuse {@link #write} (DECISIONS.md D-027).
 */
@Service
public class UpdateMapUseCase {

    private final ProjectAccessGateway accessGateway;
    private final MapRules rules;
    private final GeographicMapRepository maps;
    private final TransactionTemplate transactionTemplate;

    public UpdateMapUseCase(ProjectAccessGateway accessGateway, MapRules rules, GeographicMapRepository maps,
                            TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.maps = maps;
        this.transactionTemplate = transactionTemplate;
    }

    public GeographicMap execute(UUID projectId, UUID mapId, UnichePrincipal principal, Long version, MapView view,
                                 MapVisibility visibility, boolean visitorFilterEnabled) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            GeographicMap map = rules.requireMap(projectId, mapId);
            ResourceRules.requireCurrentVersion(map.getVersion(), version, "map");
            ResourceRules.requireDraft(map.getStatus(), "map");
            return write(projectId, map, principal, view, visibility, visitorFilterEnabled);
        });
    }

    /**
     * Validates and writes the editable state onto the map, inside the caller's transaction, without a status
     * or version check: the DRAFT save above and the working copy of an ACTIVE map share it.
     */
    public GeographicMap write(UUID projectId, GeographicMap map, UnichePrincipal principal, MapView view,
                               MapVisibility visibility, boolean visitorFilterEnabled) {
        List<ErrorItem> errors = new ArrayList<>();
        MapView checked = rules.checkView(projectId, view, map.getId(), errors);
        rules.checkVisibility(projectId, visibility, errors);
        ResourceRules.throwIfAny(errors, "The map is invalid.");
        map.edit(checked, visibility, visitorFilterEnabled, principal.subject(), Instant.now());
        return maps.saveAndFlush(map);
    }
}
