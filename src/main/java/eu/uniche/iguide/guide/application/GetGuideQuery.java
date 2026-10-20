package eu.uniche.iguide.guide.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.resource.domain.ResourceReference;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Reads one Guide, its blocks, and the rows that reference it ({@code GET .../references}). */
@Service
public class GetGuideQuery {

    private final ProjectAccessGateway accessGateway;
    private final GuideRules rules;

    public GetGuideQuery(ProjectAccessGateway accessGateway, GuideRules rules) {
        this.accessGateway = accessGateway;
        this.rules = rules;
    }

    public GuideView execute(UUID projectId, UUID guideId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return rules.view(rules.requireGuide(projectId, guideId));
    }

    public List<BlockView> blocks(UUID projectId, UUID guideId, UnichePrincipal principal) {
        return execute(projectId, guideId, principal).blocks();
    }

    /**
     * Navigation items and Interaction Triggers refer to Guides ({@code 03} section 1); neither exists yet,
     * so nothing does.
     */
    public List<ResourceReference> references(UUID projectId, UUID guideId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        rules.requireGuide(projectId, guideId);
        return List.of();
    }
}
