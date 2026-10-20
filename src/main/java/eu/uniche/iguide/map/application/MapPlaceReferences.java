package eu.uniche.iguide.map.application;

import eu.uniche.iguide.map.domain.GeographicMap;
import eu.uniche.iguide.map.infrastructure.GeographicMapRepository;
import eu.uniche.iguide.place.application.PlaceReferenceSource;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.spatial.application.SpatialRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Maps that link a Place or place it ({@code 03} section 1: {@code placeId} and placement rows). */
@Component
public class MapPlaceReferences implements PlaceReferenceSource {

    private final GeographicMapRepository maps;

    public MapPlaceReferences(GeographicMapRepository maps) {
        this.maps = maps;
    }

    @Override
    public List<ResourceReference> referencesTo(UUID projectId, UUID placeId) {
        Map<UUID, GeographicMap> found = new LinkedHashMap<>();
        maps.findByProjectIdAndPlaceId(projectId, placeId)
                .filter(map -> map.getStatus() != ResourceStatus.ARCHIVED)
                .ifPresent(map -> found.put(map.getId(), map));
        maps.findPlacing(projectId, SpatialRules.PLACE, placeId).forEach(map -> found.putIfAbsent(map.getId(), map));
        return found.values().stream()
                .map(map -> new ResourceReference("MAP", map.getId(), map.getInternalName(), map.getStatus()))
                .toList();
    }

    @Override
    public boolean linksPlace(UUID projectId, UUID placeId) {
        return maps.findByProjectIdAndPlaceId(projectId, placeId).isPresent();
    }
}
