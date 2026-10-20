package eu.uniche.iguide.poi.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import eu.uniche.iguide.poi.infrastructure.PointOfInterestRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code PUT .../pois:order}: the complete ordered list of the Project's POI ids ({@code 07} section 14).
 * A missing, repeated, or foreign id is {@code 422}.
 */
@Service
public class ReorderPoisUseCase {

    private final ProjectAccessGateway accessGateway;
    private final PointOfInterestRepository pois;
    private final TransactionTemplate transactionTemplate;

    public ReorderPoisUseCase(ProjectAccessGateway accessGateway, PointOfInterestRepository pois,
                              TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.pois = pois;
        this.transactionTemplate = transactionTemplate;
    }

    public void execute(UUID projectId, UnichePrincipal principal, List<UUID> orderedIds) {
        accessGateway.requireAccess(projectId, principal);
        transactionTemplate.executeWithoutResult(status -> {
            Set<UUID> existing = pois.findByProjectId(projectId).stream().map(PointOfInterest::getId)
                    .collect(Collectors.toSet());
            List<UUID> ids = orderedIds == null ? List.of() : orderedIds;
            if (ids.size() != existing.size() || !new HashSet<>(ids).equals(existing)) {
                throw new ValidationFailedException(
                        "The order must list every point of interest of the project exactly once.",
                        List.of(new ErrorItem("ids", "INCOMPLETE_ORDER",
                                "List every point of interest of this project exactly once.")));
            }
            for (int i = 0; i < ids.size(); i++) {
                pois.updateSortOrder(projectId, ids.get(i), i + 1);
            }
        });
    }
}
