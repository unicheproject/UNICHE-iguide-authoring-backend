package eu.uniche.iguide.place.application;

import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.infrastructure.PlaceRepository;
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

/**
 * {@code PUT .../places:order}: the complete ordered list of the Project's Place ids ({@code 07} section 14).
 * A missing, repeated, or foreign id is {@code 422}.
 */
@Service
public class ReorderPlacesUseCase {

    private final ProjectAccessGateway accessGateway;
    private final PlaceRepository places;
    private final TransactionTemplate transactionTemplate;

    public ReorderPlacesUseCase(ProjectAccessGateway accessGateway, PlaceRepository places,
                                TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.places = places;
        this.transactionTemplate = transactionTemplate;
    }

    public void execute(UUID projectId, UnichePrincipal principal, List<UUID> orderedIds) {
        accessGateway.requireAccess(projectId, principal);
        transactionTemplate.executeWithoutResult(status -> {
            Set<UUID> existing = places.findByProjectId(projectId).stream().map(Place::getId).collect(Collectors.toSet());
            List<UUID> ids = orderedIds == null ? List.of() : orderedIds;
            if (ids.size() != existing.size() || !new HashSet<>(ids).equals(existing)) {
                throw new ValidationFailedException("The order must list every place of the project exactly once.",
                        List.of(new ErrorItem("ids", "INCOMPLETE_ORDER",
                                "List every place of this project exactly once.")));
            }
            for (int i = 0; i < ids.size(); i++) {
                places.updateSortOrder(projectId, ids.get(i), i + 1);
            }
        });
    }
}
