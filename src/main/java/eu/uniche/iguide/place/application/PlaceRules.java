package eu.uniche.iguide.place.application;

import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.infrastructure.PlaceRepository;
import eu.uniche.iguide.place.infrastructure.PlaceTranslationRepository;
import eu.uniche.iguide.platform.web.DuplicateValueException;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.application.SlugGenerator;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Lookups and rules the Place use cases share. Every method takes the Project explicitly; callers have
 * already passed the Project access check.
 */
@Component
public class PlaceRules {

    private final PlaceRepository places;
    private final PlaceTranslationRepository translations;
    private final List<PlaceReferenceSource> referenceSources;

    public PlaceRules(PlaceRepository places, PlaceTranslationRepository translations,
                      List<PlaceReferenceSource> referenceSources) {
        this.places = places;
        this.translations = translations;
        this.referenceSources = referenceSources;
    }

    public Place requirePlace(UUID projectId, UUID placeId) {
        return places.findByProjectIdAndId(projectId, placeId)
                .orElseThrow(() -> new ResourceNotFoundException("No such place in this project."));
    }

    public PlaceView view(Place place) {
        return new PlaceView(place, translations.findByProjectIdAndPlaceId(place.getProjectId(), place.getId()));
    }

    /** Adds an error when the slug is malformed; throws {@code 409} when another Place uses it. */
    public void checkSlug(UUID projectId, String slug, UUID ownId, List<ErrorItem> errors) {
        if (!ResourceRules.checkSlugFormat(slug, errors)) {
            return;
        }
        boolean taken = ownId == null
                ? places.existsByProjectIdAndSlug(projectId, slug)
                : places.existsByProjectIdAndSlugAndIdNot(projectId, slug, ownId);
        if (taken) {
            throw new DuplicateValueException("slug", "Another place in this project already uses this slug.");
        }
    }

    /** A free slug from the title ({@code 03} section 5); non-Latin titles yield {@code place}. */
    public String generateSlug(UUID projectId, String title) {
        return SlugGenerator.generate(title, "place", slug -> places.existsByProjectIdAndSlug(projectId, slug));
    }

    /**
     * Adds an error when the parent is unknown in this Project, archived, the Place itself, or one of its
     * descendants, which would make the hierarchy a cycle. A parent in another Project is simply unknown:
     * the lookup is Project-scoped, and the composite foreign key would refuse it anyway.
     */
    public void checkParent(UUID projectId, UUID parentPlaceId, UUID ownId, List<ErrorItem> errors) {
        if (parentPlaceId == null) {
            return;
        }
        if (parentPlaceId.equals(ownId)) {
            errors.add(new ErrorItem("parentPlaceId", "INVALID_REFERENCE", "A place cannot be its own parent."));
            return;
        }
        Set<UUID> visited = new HashSet<>();
        UUID cursor = parentPlaceId;
        boolean directParent = true;
        while (cursor != null && visited.add(cursor)) {
            Place ancestor = places.findByProjectIdAndId(projectId, cursor).orElse(null);
            if (ancestor == null) {
                errors.add(new ErrorItem("parentPlaceId", "RESOURCE_NOT_FOUND", "No such place in this project."));
                return;
            }
            if (directParent && ancestor.getStatus() == ResourceStatus.ARCHIVED) {
                errors.add(new ErrorItem("parentPlaceId", "ARCHIVED_REFERENCE", "The parent place is archived."));
                return;
            }
            if (ownId != null && ownId.equals(ancestor.getParentPlaceId())) {
                errors.add(new ErrorItem("parentPlaceId", "CYCLE",
                        "The parent is inside this place, which would make a cycle."));
                return;
            }
            directParent = false;
            cursor = ancestor.getParentPlaceId();
        }
    }

    public List<ResourceReference> referencesTo(UUID projectId, UUID placeId) {
        return referenceSources.stream()
                .flatMap(source -> source.referencesTo(projectId, placeId).stream())
                .toList();
    }

    public boolean isLinked(UUID projectId, UUID placeId) {
        return referenceSources.stream().anyMatch(source -> source.linksPlace(projectId, placeId));
    }
}
