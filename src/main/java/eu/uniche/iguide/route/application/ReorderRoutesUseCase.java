package eu.uniche.iguide.route.application;

import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** {@code PUT .../routes:order}: the complete ordered list of the Project's Route ids ({@code 07} section 14). */
@Service
public class ReorderRoutesUseCase {

    private final ProjectAccessGateway accessGateway;
    private final RouteRepository routes;
    private final TransactionTemplate transactionTemplate;

    public ReorderRoutesUseCase(ProjectAccessGateway accessGateway, RouteRepository routes,
                                TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.routes = routes;
        this.transactionTemplate = transactionTemplate;
    }

    public void execute(UUID projectId, UnichePrincipal principal, List<UUID> orderedIds) {
        accessGateway.requireAccess(projectId, principal);
        transactionTemplate.executeWithoutResult(status -> {
            Set<UUID> existing = routes.findByProjectId(projectId).stream().map(Route::getId).collect(Collectors.toSet());
            List<UUID> ids = orderedIds == null ? List.of() : orderedIds;
            if (ids.size() != existing.size() || !new HashSet<>(ids).equals(existing)) {
                throw new ValidationFailedException("The order must list every route of the project exactly once.",
                        List.of(new ErrorItem("ids", "INCOMPLETE_ORDER", "List every route of this project exactly once.")));
            }
            for (int i = 0; i < ids.size(); i++) {
                routes.updateSortOrder(projectId, ids.get(i), i + 1);
            }
        });
    }
}
