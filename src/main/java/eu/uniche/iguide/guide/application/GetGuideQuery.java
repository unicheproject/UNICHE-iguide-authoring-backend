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
    private final List<GuideReferenceSource> referenceSources;

    public GetGuideQuery(ProjectAccessGateway accessGateway, GuideRules rules,
                         List<GuideReferenceSource> referenceSources) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.referenceSources = referenceSources;
    }

    public GuideView execute(UUID projectId, UUID guideId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return rules.view(rules.requireGuide(projectId, guideId));
    }

    public List<BlockView> blocks(UUID projectId, UUID guideId, UnichePrincipal principal) {
        return execute(projectId, guideId, principal).blocks();
    }

    /**
     * Interaction Triggers refer to Guides ({@code 03} section 1); Navigation items will once Navigation exists.
     */
    public List<ResourceReference> references(UUID projectId, UUID guideId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        rules.requireGuide(projectId, guideId);
        return referencesTo(projectId, guideId);
    }

    List<ResourceReference> referencesTo(UUID projectId, UUID guideId) {
        return referenceSources.stream()
                .flatMap(source -> source.referencesTo(projectId, guideId).stream())
                .toList();
    }
}
