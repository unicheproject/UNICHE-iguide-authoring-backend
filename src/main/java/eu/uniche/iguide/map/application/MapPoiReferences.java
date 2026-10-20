package eu.uniche.iguide.map.application;

import eu.uniche.iguide.map.infrastructure.GeographicMapRepository;
import eu.uniche.iguide.poi.application.PoiReferenceSource;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.spatial.application.SpatialRules;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Maps that place a Point of Interest ({@code 03} section 1: {@code GeographicMapResource} rows). */
@Component
public class MapPoiReferences implements PoiReferenceSource {

    private final GeographicMapRepository maps;

    public MapPoiReferences(GeographicMapRepository maps) {
        this.maps = maps;
    }

    @Override
    public List<ResourceReference> referencesTo(UUID projectId, UUID poiId) {
        return maps.findPlacing(projectId, SpatialRules.POI, poiId).stream()
                .map(map -> new ResourceReference("MAP", map.getId(), map.getInternalName(), map.getStatus()))
                .toList();
    }
}
