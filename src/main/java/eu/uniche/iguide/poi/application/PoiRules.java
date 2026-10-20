package eu.uniche.iguide.poi.application;

import eu.uniche.iguide.asset.application.ResourceMedia;
import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.asset.domain.ResourceAsset;
import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.category.domain.ResourceCategory;
import eu.uniche.iguide.category.infrastructure.ResourceCategoryRepository;
import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.infrastructure.PlaceRepository;
import eu.uniche.iguide.platform.web.DuplicateValueException;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import eu.uniche.iguide.poi.infrastructure.PoiTranslationRepository;
import eu.uniche.iguide.poi.infrastructure.PointOfInterestRepository;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.application.SlugGenerator;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Lookups and rules the Point of Interest use cases share. Every method takes the Project explicitly;
 * callers have already passed the Project access check.
 */
@Component
public class PoiRules {

    /** Letters, digits, and single hyphens, case kept as typed (DECISIONS.md D-020). */
    public static final Pattern CODE = Pattern.compile("^[A-Za-z0-9]+(-[A-Za-z0-9]+)*$");
    public static final int MAX_CODE = 32;
    public static final int MAX_VISIT_MINUTES = 1440;

    private final PointOfInterestRepository pois;
    private final PoiTranslationRepository translations;
    private final PlaceRepository places;
    private final ResourceCategoryRepository categories;
    private final List<PoiReferenceSource> referenceSources;
    private final ResourceMedia media;

    public PoiRules(PointOfInterestRepository pois, PoiTranslationRepository translations, PlaceRepository places,
                    ResourceCategoryRepository categories, List<PoiReferenceSource> referenceSources,
                    ResourceMedia media) {
        this.pois = pois;
        this.translations = translations;
        this.places = places;
        this.categories = categories;
        this.referenceSources = referenceSources;
        this.media = media;
    }

    public PointOfInterest requirePoi(UUID projectId, UUID poiId) {
        return pois.findByProjectIdAndId(projectId, poiId)
                .orElseThrow(() -> new ResourceNotFoundException("No such point of interest in this project."));
    }

    public PoiView view(PointOfInterest poi) {
        return new PoiView(poi, translations.findByProjectIdAndPoiId(poi.getProjectId(), poi.getId()),
                categoryIds(poi.getProjectId(), List.of(poi.getId())).getOrDefault(poi.getId(), List.of()),
                media(poi.getProjectId(), List.of(poi.getId())).getOrDefault(poi.getId(), List.of()));
    }

    /** The media bindings of several Points of Interest of one Project, by POI id. */
    public Map<UUID, List<ResourceAsset>> media(UUID projectId, List<UUID> poiIds) {
        return media.of(projectId, AssetResourceType.POI, poiIds);
    }

    /** The Categories of several Points of Interest of one Project, by POI id. */
    public Map<UUID, List<UUID>> categoryIds(UUID projectId, List<UUID> poiIds) {
        if (poiIds.isEmpty()) {
            return Map.of();
        }
        return categories.findByProjectIdAndResourceTypeAndResourceIdIn(projectId, CategoryScope.POI, poiIds).stream()
                .collect(Collectors.groupingBy(ResourceCategory::getResourceId,
                        Collectors.mapping(ResourceCategory::getCategoryId, Collectors.toList())));
    }

    /** Adds an error when the slug is malformed; throws {@code 409} when another POI uses it. */
    public void checkSlug(UUID projectId, String slug, UUID ownId, List<ErrorItem> errors) {
        if (!ResourceRules.checkSlugFormat(slug, errors)) {
            return;
        }
        boolean taken = ownId == null
                ? pois.existsByProjectIdAndSlug(projectId, slug)
                : pois.existsByProjectIdAndSlugAndIdNot(projectId, slug, ownId);
        if (taken) {
            throw new DuplicateValueException("slug", "Another point of interest in this project already uses this slug.");
        }
    }

    public String generateSlug(UUID projectId, String title) {
        return SlugGenerator.generate(title, "point-of-interest", slug -> pois.existsByProjectIdAndSlug(projectId, slug));
    }

    /**
     * Adds an error when the exhibit code is malformed; throws {@code 409} when another POI uses it
     * ({@code 06} section 12). A null code is no code.
     */
    public void checkCode(UUID projectId, String code, UUID ownId, List<ErrorItem> errors) {
        if (code == null) {
            return;
        }
        if (code.length() > MAX_CODE || !CODE.matcher(code).matches()) {
            errors.add(new ErrorItem("code", "INVALID_FORMAT",
                    "Use letters, digits, and single hyphens, at most " + MAX_CODE + " characters."));
            return;
        }
        boolean taken = ownId == null
                ? pois.existsByProjectIdAndCode(projectId, code)
                : pois.existsByProjectIdAndCodeAndIdNot(projectId, code, ownId);
        if (taken) {
            throw new DuplicateValueException("code", "Another point of interest in this project already uses this exhibit code.");
        }
    }

    /** The Place the POI is inside: same Project and not archived. Its environment is not checked (D-020). */
    public void checkPlace(UUID projectId, UUID placeId, List<ErrorItem> errors) {
        if (placeId == null) {
            return;
        }
        Optional<Place> place = places.findByProjectIdAndId(projectId, placeId);
        if (place.isEmpty()) {
            errors.add(new ErrorItem("placeId", "RESOURCE_NOT_FOUND", "No such place in this project."));
        } else if (place.get().getStatus() == ResourceStatus.ARCHIVED) {
            errors.add(new ErrorItem("placeId", "ARCHIVED_REFERENCE", "The place is archived."));
        }
    }

    public static void checkVisitMinutes(Integer minutes, List<ErrorItem> errors) {
        if (minutes != null && (minutes < 1 || minutes > MAX_VISIT_MINUTES)) {
            errors.add(new ErrorItem("estimatedVisitMinutes", "OUT_OF_RANGE",
                    "Must be between 1 and " + MAX_VISIT_MINUTES + "."));
        }
    }

    public List<ResourceReference> referencesTo(UUID projectId, UUID poiId) {
        return referenceSources.stream()
                .flatMap(source -> source.referencesTo(projectId, poiId).stream())
                .toList();
    }
}
