package eu.uniche.iguide.route.application;

import eu.uniche.iguide.language.application.TitledResourceCounter;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
import eu.uniche.iguide.route.infrastructure.RouteTranslationRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Routes' share of the translation completeness counts. */
@Component
public class RouteTitleCounter implements TitledResourceCounter {

    private final RouteRepository routes;
    private final RouteTranslationRepository translations;

    public RouteTitleCounter(RouteRepository routes, RouteTranslationRepository translations) {
        this.routes = routes;
        this.translations = translations;
    }

    @Override
    public long countResources(UUID projectId) {
        return routes.countByProjectIdAndStatusNot(projectId, ResourceStatus.ARCHIVED);
    }

    @Override
    public long countTitled(UUID projectId, String languageCode) {
        return translations.countTitled(projectId, languageCode, ResourceStatus.ARCHIVED);
    }
}
