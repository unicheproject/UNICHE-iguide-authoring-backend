package eu.uniche.iguide.route.application;

import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.domain.RouteTranslation;
import eu.uniche.iguide.route.domain.RouteType;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
import eu.uniche.iguide.route.infrastructure.RouteTranslationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates a DRAFT Route from its type and its title in the default language ({@code 12} section 3), with
 * no stops yet, last in the Project's order.
 */
@Service
public class CreateRouteUseCase {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final RouteRules rules;
    private final RouteRepository routes;
    private final RouteTranslationRepository translations;
    private final TransactionTemplate transactionTemplate;

    public CreateRouteUseCase(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages, RouteRules rules,
                              RouteRepository routes, RouteTranslationRepository translations,
                              TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.rules = rules;
        this.routes = routes;
        this.translations = translations;
        this.transactionTemplate = transactionTemplate;
    }

    public RouteView execute(UUID projectId, UnichePrincipal principal, String rawTitle, String rawSlug, RouteType type) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            String defaultLanguage = languages.requireDefaultLanguage(projectId);
            List<ErrorItem> errors = new ArrayList<>();
            String title = ResourceRules.trimToNull(rawTitle);
            if (title == null) {
                errors.add(new ErrorItem("title", "REQUIRED", "Is required in the default language."));
            }
            if (type == null) {
                errors.add(new ErrorItem("type", "REQUIRED", "Choose INDOOR or OUTDOOR."));
            }
            String slug = ResourceRules.trimToNull(rawSlug);
            if (slug != null) {
                rules.checkSlug(projectId, slug, null, errors);
            }
            ResourceRules.throwIfAny(errors, "The route is invalid.");
            if (slug == null) {
                slug = rules.generateSlug(projectId, title);
            }
            Route route = routes.save(Route.draft(projectId, slug, type, routes.findMaxSortOrder(projectId) + 1,
                    principal.subject(), Instant.now()));
            RouteTranslation translation = RouteTranslation.of(projectId, route.getId(), defaultLanguage);
            translation.write(title, null, null, null);
            translations.save(translation);
            return rules.view(route);
        });
    }
}
