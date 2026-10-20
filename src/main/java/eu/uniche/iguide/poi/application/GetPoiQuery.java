package eu.uniche.iguide.poi.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.resource.domain.ResourceReference;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Reads one Point of Interest, and the rows that reference it ({@code GET .../references}). */
@Service
public class GetPoiQuery {

    private final ProjectAccessGateway accessGateway;
    private final PoiRules rules;

    public GetPoiQuery(ProjectAccessGateway accessGateway, PoiRules rules) {
        this.accessGateway = accessGateway;
        this.rules = rules;
    }

    public PoiView execute(UUID projectId, UUID poiId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return rules.view(rules.requirePoi(projectId, poiId));
    }

    public List<ResourceReference> references(UUID projectId, UUID poiId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        rules.requirePoi(projectId, poiId);
        return rules.referencesTo(projectId, poiId);
    }
}
