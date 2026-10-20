package eu.uniche.iguide.poi.application;

import eu.uniche.iguide.asset.application.AssetBindingTarget;
import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import eu.uniche.iguide.poi.domain.PoiTranslation;
import eu.uniche.iguide.poi.infrastructure.PointOfInterestRepository;
import eu.uniche.iguide.poi.infrastructure.PoiTranslationRepository;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Points of Interest bind Assets through {@code .../pois/{id}/resource-assets} (DECISIONS.md D-025). */
@Component
public class PoiAssetTarget implements AssetBindingTarget {

    private final PointOfInterestRepository resources;
    private final PoiTranslationRepository translations;
    private final ProjectLanguageLookup languages;

    public PoiAssetTarget(PointOfInterestRepository resources, PoiTranslationRepository translations, ProjectLanguageLookup languages) {
        this.resources = resources;
        this.translations = translations;
        this.languages = languages;
    }

    @Override
    public AssetResourceType kind() {
        return AssetResourceType.POI;
    }

    @Override
    public Optional<ResourceStatus> status(UUID projectId, UUID resourceId) {
        return resources.findByProjectIdAndId(projectId, resourceId).map(PointOfInterest::getStatus);
    }

    @Override
    public List<ResourceReference> references(UUID projectId, Collection<UUID> resourceIds) {
        if (resourceIds.isEmpty()) {
            return List.of();
        }
        String defaultLanguage = languages.requireDefaultLanguage(projectId);
        Map<UUID, String> titles = translations.findByProjectIdAndPoiIdIn(projectId, resourceIds).stream()
                .filter(t -> t.getLanguageCode().equals(defaultLanguage) && t.hasTitle())
                .collect(Collectors.toMap(PoiTranslation::getPoiId, PoiTranslation::getTitle));
        return resources.findByProjectIdAndIdIn(projectId, resourceIds).stream()
                .map(r -> new ResourceReference("POI", r.getId(), titles.getOrDefault(r.getId(), r.getSlug()),
                        r.getStatus()))
                .toList();
    }
}
