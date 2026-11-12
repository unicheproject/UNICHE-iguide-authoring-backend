package eu.uniche.iguide.map.application;

import eu.uniche.iguide.map.application.MapPlacementUseCase.Wanted;
import eu.uniche.iguide.map.domain.GeographicMapResource;
import eu.uniche.iguide.map.infrastructure.GeographicMapResourceRepository;
import eu.uniche.iguide.platform.web.DuplicateValueException;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.resource.application.ErrorPaths;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.spatial.application.SpatialRules;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The rules and row writes of a map's placements, shared by the placement endpoints and the apply of a working
 * copy. Every method runs inside the caller's transaction, without a status check.
 */
@Component
public class MapPlacementWriter {

    static final String DUPLICATE = "The resource is already placed on this map.";

    private final SpatialRules spatial;
    private final GeographicMapResourceRepository placements;

    public MapPlacementWriter(SpatialRules spatial, GeographicMapResourceRepository placements) {
        this.spatial = spatial;
        this.placements = placements;
    }

    /**
     * Makes the map's placements exactly {@code wanted}: the apply of a working copy (DECISIONS.md D-027). A wanted
     * entry keeps the placement its {@code id} names, or else one of the same Resource, and moves it; the others are
     * placed with the rules of {@link #place}; placements no entry claims are removed. Errors are reported at
     * {@code placements[i]}.
     */
    public List<GeographicMapResource> replacePlacements(UUID projectId, UUID mapId, List<Wanted> wanted) {
        List<Wanted> desired = wanted == null ? List.of() : wanted;
        List<GeographicMapResource> unclaimed = new ArrayList<>(
                placements.findByProjectIdAndMapIdOrderByIdAsc(projectId, mapId));
        GeographicMapResource[] kept = new GeographicMapResource[desired.size()];
        Set<String> targets = new HashSet<>();
        for (int i = 0; i < desired.size(); i++) {
            Wanted w = desired.get(i);
            if (!targets.add(w.resourceType() + ":" + w.resourceId())) {
                throw new DuplicateValueException("placements[" + i + "].resourceId", DUPLICATE);
            }
            GeographicMapResource match = unclaimed.stream()
                    .filter(p -> w.id() != null && p.getId().equals(w.id()) && sameTarget(p, w))
                    .findFirst()
                    .or(() -> unclaimed.stream().filter(p -> sameTarget(p, w)).findFirst())
                    .orElse(null);
            if (match != null) {
                unclaimed.remove(match);
                kept[i] = match;
            }
        }
        placements.deleteAll(unclaimed);
        placements.flush();
        List<GeographicMapResource> result = new ArrayList<>();
        for (int i = 0; i < desired.size(); i++) {
            Wanted w = desired.get(i);
            GeographicMapResource existing = kept[i];
            result.add(ErrorPaths.within("placements[" + i + "]", () -> existing != null
                    ? move(existing, w.latitude(), w.longitude())
                    : place(projectId, mapId, w.resourceType(), w.resourceId(), w.latitude(), w.longitude())));
        }
        return result;
    }

    /** The checks of a new placement other than the duplicate one: a Place or POI of the Project, coordinates. */
    void checkNew(UUID projectId, String resourceType, UUID resourceId, BigDecimal latitude, BigDecimal longitude) {
        List<ErrorItem> errors = new ArrayList<>();
        spatial.checkTarget(projectId, resourceType, resourceId, errors);
        ResourceRules.checkLatitude(latitude, "latitude", errors);
        ResourceRules.checkLongitude(longitude, "longitude", errors);
        ResourceRules.throwIfAny(errors, "The placement is invalid.");
    }

    static void checkMove(BigDecimal latitude, BigDecimal longitude) {
        List<ErrorItem> errors = new ArrayList<>();
        ResourceRules.checkLatitude(latitude, "latitude", errors);
        ResourceRules.checkLongitude(longitude, "longitude", errors);
        ResourceRules.throwIfAny(errors, "The placement is invalid.");
    }

    GeographicMapResource place(UUID projectId, UUID mapId, String resourceType, UUID resourceId,
                                BigDecimal latitude, BigDecimal longitude) {
        checkNew(projectId, resourceType, resourceId, latitude, longitude);
        if (placements.existsByProjectIdAndMapIdAndResourceTypeAndResourceId(projectId, mapId, resourceType,
                resourceId)) {
            throw new DuplicateValueException("resourceId", DUPLICATE);
        }
        return placements.saveAndFlush(GeographicMapResource.of(projectId, mapId, resourceType, resourceId,
                latitude, longitude));
    }

    GeographicMapResource move(GeographicMapResource placement, BigDecimal latitude, BigDecimal longitude) {
        checkMove(latitude, longitude);
        placement.moveTo(latitude, longitude);
        return placements.saveAndFlush(placement);
    }

    private static boolean sameTarget(GeographicMapResource placement, Wanted wanted) {
        return Objects.equals(placement.getResourceType(), wanted.resourceType())
                && Objects.equals(placement.getResourceId(), wanted.resourceId());
    }
}
