package eu.uniche.iguide.language.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GetProjectLanguagesQuery {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguagesLoader loader;

    public GetProjectLanguagesQuery(ProjectAccessGateway accessGateway, ProjectLanguagesLoader loader) {
        this.accessGateway = accessGateway;
        this.loader = loader;
    }

    public ProjectLanguagesView execute(UUID projectId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return loader.load(projectId);
    }
}
