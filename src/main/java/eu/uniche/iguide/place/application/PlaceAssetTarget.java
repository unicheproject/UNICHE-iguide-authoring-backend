package eu.uniche.iguide.place.application;

import eu.uniche.iguide.asset.application.AssetBindingTarget;
import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.domain.PlaceTranslation;
import eu.uniche.iguide.place.infrastructure.PlaceRepository;
import eu.uniche.iguide.place.infrastructure.PlaceTranslationRepository;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Places bind Assets through {@code .../places/{id}/resource-assets} (DECISIONS.md D-041). */
@Component
public class PlaceAssetTarget implements AssetBindingTarget {

    private final PlaceRepository resources;
    private final PlaceTranslationRepository translations;
    private final ProjectLanguageLookup languages;

    public PlaceAssetTarget(PlaceRepository resources, PlaceTranslationRepository translations, ProjectLanguageLookup languages) {
        this.resources = resources;
        this.translations = translations;
        this.languages = languages;
    }

    @Override
    public AssetResourceType kind() {
        return AssetResourceType.PLACE;
    }

    @Override
    public Optional<ResourceStatus> status(UUID projectId, UUID resourceId) {
        return resources.findByProjectIdAndId(projectId, resourceId).map(Place::getStatus);
    }

    @Override
    public List<ResourceReference> references(UUID projectId, Collection<UUID> resourceIds) {
        if (resourceIds.isEmpty()) {
            return List.of();
        }
        String defaultLanguage = languages.requireDefaultLanguage(projectId);
        Map<UUID, String> titles = translations.findByProjectIdAndPlaceIdIn(projectId, resourceIds).stream()
                .filter(t -> t.getLanguageCode().equals(defaultLanguage) && t.hasTitle())
                .collect(Collectors.toMap(PlaceTranslation::getPlaceId, PlaceTranslation::getTitle));
        return resources.findByProjectIdAndIdIn(projectId, resourceIds).stream()
                .map(r -> new ResourceReference("PLACE", r.getId(), titles.getOrDefault(r.getId(), r.getSlug()),
                        r.getStatus()))
                .toList();
    }
}
