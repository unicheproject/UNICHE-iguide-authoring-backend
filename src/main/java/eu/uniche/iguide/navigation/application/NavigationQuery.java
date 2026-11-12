package eu.uniche.iguide.navigation.application;

import eu.uniche.iguide.navigation.infrastructure.NavigationRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Reads a Project's Navigations, each with its items in tree order ({@code 07} section 14). */
@Service
public class NavigationQuery {

    private final ProjectAccessGateway accessGateway;
    private final NavigationRules rules;
    private final NavigationRepository navigations;
    private final TransactionTemplate transactionTemplate;

    public NavigationQuery(ProjectAccessGateway accessGateway, NavigationRules rules, NavigationRepository navigations,
                           TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.navigations = navigations;
        this.transactionTemplate = transactionTemplate;
    }

    /** The Project's Navigations: none, PRIMARY, FOOTER, or both. */
    public List<NavigationView> list(UUID projectId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status ->
                rules.views(projectId, navigations.findByProjectIdOrderByContextAsc(projectId)));
    }

    public NavigationView get(UUID projectId, UUID navigationId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> rules.view(rules.requireNavigation(projectId, navigationId)));
    }
}
