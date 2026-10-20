package eu.uniche.iguide.spatial.application;

import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.domain.PlaceEnvironment;
import eu.uniche.iguide.place.infrastructure.PlaceRepository;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import eu.uniche.iguide.poi.infrastructure.PointOfInterestRepository;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Rules Geographic Maps and Floor Plans share: the Place they link to, and the Resources placed on them.
 * A generic {@code (resourceType, resourceId)} target must exist in the same Project, be of a supported
 * type, and not be archived ({@code 06} section 5).
 */
@Component
public class SpatialRules {

    /** The placement types the schema accepts. */
    public static final String PLACE = "PLACE";
    public static final String POI = "POI";

    private final PlaceRepository places;
    private final PointOfInterestRepository pois;

    public SpatialRules(PlaceRepository places, PointOfInterestRepository pois) {
        this.places = places;
        this.pois = pois;
    }

    /**
     * Checks the Place a map or floor plan links to: same Project, not archived, and of the environment
     * the kind serves ({@code 03} section 5). A null id is no link.
     */
    public void checkLinkedPlace(UUID projectId, UUID placeId, PlaceEnvironment required, List<ErrorItem> errors) {
        if (placeId == null) {
            return;
        }
        Optional<Place> place = places.findByProjectIdAndId(projectId, placeId);
        if (place.isEmpty()) {
            errors.add(new ErrorItem("placeId", "RESOURCE_NOT_FOUND", "No such place in this project."));
        } else if (place.get().getStatus() == ResourceStatus.ARCHIVED) {
            errors.add(new ErrorItem("placeId", "ARCHIVED_REFERENCE", "The place is archived."));
        } else if (place.get().getEnvironment() != required) {
            errors.add(new ErrorItem("placeId", "ENVIRONMENT_MISMATCH", required == PlaceEnvironment.OUTDOOR
                    ? "The place is indoors; link a floor plan to it instead."
                    : "The place is outdoors; link a map to it instead."));
        }
    }

    /** Checks one placement target: a Place or a Point of Interest of this Project, not archived. */
    public void checkTarget(UUID projectId, String resourceType, UUID resourceId, List<ErrorItem> errors) {
        if (!PLACE.equals(resourceType) && !POI.equals(resourceType)) {
            errors.add(new ErrorItem("resourceType", "UNSUPPORTED_TYPE", "Place a PLACE or a POI."));
            return;
        }
        if (resourceId == null) {
            errors.add(new ErrorItem("resourceId", "REQUIRED", "Is required."));
            return;
        }
        Optional<ResourceStatus> status = PLACE.equals(resourceType)
                ? places.findByProjectIdAndId(projectId, resourceId).map(Place::getStatus)
                : pois.findByProjectIdAndId(projectId, resourceId).map(PointOfInterest::getStatus);
        if (status.isEmpty()) {
            errors.add(new ErrorItem("resourceId", "RESOURCE_NOT_FOUND", "No such resource in this project."));
        } else if (status.get() == ResourceStatus.ARCHIVED) {
            errors.add(new ErrorItem("resourceId", "ARCHIVED_REFERENCE", "The resource is archived."));
        }
    }

    public static <T> T require(Optional<T> found, String kind) {
        return found.orElseThrow(() -> new ResourceNotFoundException("No such " + kind + " in this project."));
    }
}
