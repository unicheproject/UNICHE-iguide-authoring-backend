package eu.uniche.iguide.guide.application;

import eu.uniche.iguide.guide.domain.Guide;
import eu.uniche.iguide.guide.infrastructure.GuideRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** {@code PUT .../guides:order}: the complete ordered list of the Project's Guide ids ({@code 07} section 14). */
@Service
public class ReorderGuidesUseCase {

    private final ProjectAccessGateway accessGateway;
    private final GuideRepository guides;
    private final TransactionTemplate transactionTemplate;

    public ReorderGuidesUseCase(ProjectAccessGateway accessGateway, GuideRepository guides,
                                TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.guides = guides;
        this.transactionTemplate = transactionTemplate;
    }

    public void execute(UUID projectId, UnichePrincipal principal, List<UUID> orderedIds) {
        accessGateway.requireAccess(projectId, principal);
        transactionTemplate.executeWithoutResult(status -> {
            Set<UUID> existing = guides.findByProjectId(projectId).stream().map(Guide::getId).collect(Collectors.toSet());
            List<UUID> ids = orderedIds == null ? List.of() : orderedIds;
            if (ids.size() != existing.size() || !new HashSet<>(ids).equals(existing)) {
                throw new ValidationFailedException("The order must list every guide of the project exactly once.",
                        List.of(new ErrorItem("ids", "INCOMPLETE_ORDER", "List every guide of this project exactly once.")));
            }
            for (int i = 0; i < ids.size(); i++) {
                guides.updateSortOrder(projectId, ids.get(i), i + 1);
            }
        });
    }
}
