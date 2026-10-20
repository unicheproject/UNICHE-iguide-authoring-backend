package eu.uniche.iguide.guide.application;

import eu.uniche.iguide.asset.application.AssetBindingTarget;
import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.guide.domain.Guide;
import eu.uniche.iguide.guide.domain.GuideTranslation;
import eu.uniche.iguide.guide.infrastructure.GuideRepository;
import eu.uniche.iguide.guide.infrastructure.GuideTranslationRepository;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Guides bind Assets through {@code .../guides/{id}/resource-assets} (DECISIONS.md D-025). */
@Component
public class GuideAssetTarget implements AssetBindingTarget {

    private final GuideRepository resources;
    private final GuideTranslationRepository translations;
    private final ProjectLanguageLookup languages;

    public GuideAssetTarget(GuideRepository resources, GuideTranslationRepository translations, ProjectLanguageLookup languages) {
        this.resources = resources;
        this.translations = translations;
        this.languages = languages;
    }

    @Override
    public AssetResourceType kind() {
        return AssetResourceType.GUIDE;
    }

    @Override
    public Optional<ResourceStatus> status(UUID projectId, UUID resourceId) {
        return resources.findByProjectIdAndId(projectId, resourceId).map(Guide::getStatus);
    }

    @Override
    public List<ResourceReference> references(UUID projectId, Collection<UUID> resourceIds) {
        if (resourceIds.isEmpty()) {
            return List.of();
        }
        String defaultLanguage = languages.requireDefaultLanguage(projectId);
        Map<UUID, String> titles = translations.findByProjectIdAndGuideIdIn(projectId, resourceIds).stream()
                .filter(t -> t.getLanguageCode().equals(defaultLanguage) && t.hasTitle())
                .collect(Collectors.toMap(GuideTranslation::getGuideId, GuideTranslation::getTitle));
        return resources.findByProjectIdAndIdIn(projectId, resourceIds).stream()
                .map(r -> new ResourceReference("GUIDE", r.getId(), titles.getOrDefault(r.getId(), r.getSlug()),
                        r.getStatus()))
                .toList();
    }
}
