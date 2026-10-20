package eu.uniche.iguide.place.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.resource.domain.ResourceReference;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Reads one Place, and the rows that reference it ({@code GET .../references}). */
@Service
public class GetPlaceQuery {

    private final ProjectAccessGateway accessGateway;
    private final PlaceRules rules;

    public GetPlaceQuery(ProjectAccessGateway accessGateway, PlaceRules rules) {
        this.accessGateway = accessGateway;
        this.rules = rules;
    }

    public PlaceView execute(UUID projectId, UUID placeId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return rules.view(rules.requirePlace(projectId, placeId));
    }

    public List<ResourceReference> references(UUID projectId, UUID placeId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        rules.requirePlace(projectId, placeId);
        return rules.referencesTo(projectId, placeId);
    }
}
