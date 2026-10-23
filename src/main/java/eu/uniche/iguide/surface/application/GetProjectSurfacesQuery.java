package eu.uniche.iguide.surface.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** {@code GET .../surfaces}: WEB, MOBILE, and KIOSK in order, each enabled or not, with its configuration. */
@Service
public class GetProjectSurfacesQuery {

    private final ProjectAccessGateway accessGateway;
    private final SurfaceLookup lookup;

    public GetProjectSurfacesQuery(ProjectAccessGateway accessGateway, SurfaceLookup lookup) {
        this.accessGateway = accessGateway;
        this.lookup = lookup;
    }

    public List<SurfaceView> execute(UUID projectId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return lookup.all(projectId);
    }
}
