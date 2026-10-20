package eu.uniche.iguide.route.application;

import eu.uniche.iguide.asset.application.AssetBindingTarget;
import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.route.domain.Route;
import eu.uniche.iguide.route.domain.RouteTranslation;
import eu.uniche.iguide.route.infrastructure.RouteRepository;
import eu.uniche.iguide.route.infrastructure.RouteTranslationRepository;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Routes bind Assets through {@code .../routes/{id}/resource-assets} (DECISIONS.md D-025). */
@Component
public class RouteAssetTarget implements AssetBindingTarget {

    private final RouteRepository resources;
    private final RouteTranslationRepository translations;
    private final ProjectLanguageLookup languages;

    public RouteAssetTarget(RouteRepository resources, RouteTranslationRepository translations, ProjectLanguageLookup languages) {
        this.resources = resources;
        this.translations = translations;
        this.languages = languages;
    }

    @Override
    public AssetResourceType kind() {
        return AssetResourceType.ROUTE;
    }

    @Override
    public Optional<ResourceStatus> status(UUID projectId, UUID resourceId) {
        return resources.findByProjectIdAndId(projectId, resourceId).map(Route::getStatus);
    }

    @Override
    public List<ResourceReference> references(UUID projectId, Collection<UUID> resourceIds) {
        if (resourceIds.isEmpty()) {
            return List.of();
        }
        String defaultLanguage = languages.requireDefaultLanguage(projectId);
        Map<UUID, String> titles = translations.findByProjectIdAndRouteIdIn(projectId, resourceIds).stream()
                .filter(t -> t.getLanguageCode().equals(defaultLanguage) && t.hasTitle())
                .collect(Collectors.toMap(RouteTranslation::getRouteId, RouteTranslation::getTitle));
        return resources.findByProjectIdAndIdIn(projectId, resourceIds).stream()
                .map(r -> new ResourceReference("ROUTE", r.getId(), titles.getOrDefault(r.getId(), r.getSlug()),
                        r.getStatus()))
                .toList();
    }
}
