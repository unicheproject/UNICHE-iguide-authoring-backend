package eu.uniche.iguide.map.application;

import eu.uniche.iguide.map.domain.GeographicMap;
import eu.uniche.iguide.map.domain.GeographicMapResource;
import eu.uniche.iguide.map.infrastructure.GeographicMapRepository;
import eu.uniche.iguide.map.infrastructure.GeographicMapResourceRepository;
import eu.uniche.iguide.readiness.application.ReadinessCheck;
import eu.uniche.iguide.readiness.application.ReadinessContext;
import eu.uniche.iguide.readiness.application.ReadinessReport;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Readiness of ACTIVE maps (DECISIONS.md D-033): a placed Resource that is gone or archived. */
@Component
public class MapReadiness implements ReadinessCheck {

    private final GeographicMapRepository maps;
    private final GeographicMapResourceRepository placements;

    public MapReadiness(GeographicMapRepository maps, GeographicMapResourceRepository placements) {
        this.maps = maps;
        this.placements = placements;
    }

    @Override
    public void check(ReadinessContext context, ReadinessReport report) {
        List<GeographicMap> active = maps.findByProjectIdAndStatus(context.projectId(), ResourceStatus.ACTIVE);
        if (active.isEmpty()) {
            return;
        }
        Map<UUID, List<GeographicMapResource>> byMap = placements.findByProjectIdAndMapIdInOrderByIdAsc(
                        context.projectId(), active.stream().map(GeographicMap::getId).toList()).stream()
                .collect(Collectors.groupingBy(GeographicMapResource::getMapId));
        for (GeographicMap map : active) {
            List<GeographicMapResource> own = byMap.getOrDefault(map.getId(), List.of());
            for (int i = 0; i < own.size(); i++) {
                if (context.index().dangling(own.get(i).getResourceType(), own.get(i).getResourceId())) {
                    report.block("MAP", map.getId(), map.getInternalName(), null, "placements[" + i + "]",
                            "DANGLING_REFERENCE");
                }
            }
        }
    }
}
