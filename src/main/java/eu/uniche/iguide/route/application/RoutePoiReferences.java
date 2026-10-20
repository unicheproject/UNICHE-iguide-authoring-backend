package eu.uniche.iguide.route.application;

import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.poi.application.PoiReferenceSource;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.domain.RouteTranslation;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
import eu.uniche.iguide.route.infrastructure.RouteTranslationRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Routes with a stop on a Point of Interest ({@code 03} section 1: {@code RouteStop} rows), titled in the
 * default language, or by slug when that title is missing.
 */
@Component
public class RoutePoiReferences implements PoiReferenceSource {

    private final RouteRepository routes;
    private final RouteTranslationRepository translations;
    private final ProjectLanguageLookup languages;

    public RoutePoiReferences(RouteRepository routes, RouteTranslationRepository translations,
                              ProjectLanguageLookup languages) {
        this.routes = routes;
        this.translations = translations;
        this.languages = languages;
    }

    @Override
    public List<ResourceReference> referencesTo(UUID projectId, UUID poiId) {
        List<Route> using = routes.findUsingPoi(projectId, poiId);
        if (using.isEmpty()) {
            return List.of();
        }
        String defaultLanguage = languages.requireDefaultLanguage(projectId);
        Map<UUID, String> titles = translations.findByProjectIdAndRouteIdIn(projectId,
                        using.stream().map(Route::getId).toList()).stream()
                .filter(t -> t.getLanguageCode().equals(defaultLanguage) && t.hasTitle())
                .collect(Collectors.toMap(RouteTranslation::getRouteId, RouteTranslation::getTitle));
        return using.stream()
                .map(r -> new ResourceReference("ROUTE", r.getId(), titles.getOrDefault(r.getId(), r.getSlug()), r.getStatus()))
                .toList();
    }
}
