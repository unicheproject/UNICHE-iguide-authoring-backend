package eu.uniche.iguide.event.application;

import eu.uniche.iguide.asset.application.AssetBindingTarget;
import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.event.domain.Event;
import eu.uniche.iguide.event.domain.EventTranslation;
import eu.uniche.iguide.event.infrastructure.EventRepository;
import eu.uniche.iguide.event.infrastructure.EventTranslationRepository;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Events bind Assets through {@code .../events/{id}/resource-assets} (DECISIONS.md D-025). */
@Component
public class EventAssetTarget implements AssetBindingTarget {

    private final EventRepository resources;
    private final EventTranslationRepository translations;
    private final ProjectLanguageLookup languages;

    public EventAssetTarget(EventRepository resources, EventTranslationRepository translations, ProjectLanguageLookup languages) {
        this.resources = resources;
        this.translations = translations;
        this.languages = languages;
    }

    @Override
    public AssetResourceType kind() {
        return AssetResourceType.EVENT;
    }

    @Override
    public Optional<ResourceStatus> status(UUID projectId, UUID resourceId) {
        return resources.findByProjectIdAndId(projectId, resourceId).map(Event::getStatus);
    }

    @Override
    public List<ResourceReference> references(UUID projectId, Collection<UUID> resourceIds) {
        if (resourceIds.isEmpty()) {
            return List.of();
        }
        String defaultLanguage = languages.requireDefaultLanguage(projectId);
        Map<UUID, String> titles = translations.findByProjectIdAndEventIdIn(projectId, resourceIds).stream()
                .filter(t -> t.getLanguageCode().equals(defaultLanguage) && t.hasTitle())
                .collect(Collectors.toMap(EventTranslation::getEventId, EventTranslation::getTitle));
        return resources.findByProjectIdAndIdIn(projectId, resourceIds).stream()
                .map(r -> new ResourceReference("EVENT", r.getId(), titles.getOrDefault(r.getId(), r.getSlug()),
                        r.getStatus()))
                .toList();
    }
}
