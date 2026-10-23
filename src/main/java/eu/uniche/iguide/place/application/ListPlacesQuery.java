package eu.uniche.iguide.place.application;

import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.domain.PlaceTranslation;
import eu.uniche.iguide.place.infrastructure.PlaceRepository;
import eu.uniche.iguide.place.infrastructure.PlaceTranslationRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.resource.application.ListFilters;
import eu.uniche.iguide.resource.application.ListParams;
import eu.uniche.iguide.resource.application.PageRequests;
import eu.uniche.iguide.workingcopy.application.WorkingCopyStore;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * Pages through a Project's Places in {@code sortOrder} ({@code 06} section 16), filtered by status and
 * by {@code q}, a case-insensitive match on the title in any language, and by {@code pending}, the Places with
 * a working copy ({@code 07} section 14).
 *
 * <p>{@code language} narrows {@code q} to one language, {@code sort} names an indexed column, and the other
 * filters of {@link eu.uniche.iguide.resource.application.ListParams} apply (DECISIONS.md D-030).
 */
@Service
public class ListPlacesQuery {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final PlaceRepository places;
    private final PlaceTranslationRepository translations;

    public ListPlacesQuery(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages,
                           PlaceRepository places,
                           PlaceTranslationRepository translations) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.places = places;
        this.translations = translations;
    }

    public Page<PlaceView> execute(UUID projectId, UnichePrincipal principal, ListParams params) {
        accessGateway.requireAccess(projectId, principal);
        String language = ListFilters.requireLanguage(params.language(), languages.enabledCodes(projectId));
        Specification<Place> spec = (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
        if (params.status() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), params.status()));
        }
        if (params.featured() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("featured"), params.featured()));
        }
        if (params.pending()) {
            spec = spec.and(WorkingCopyStore.pending(projectId, WorkingCopyResourceType.PLACE));
        }
        String pattern = ListFilters.likePattern(params.q());
        if (pattern != null) {
            spec = spec.and((root, query, cb) -> {
                var matched = ListFilters.translationMatches(root, query, cb, projectId, pattern, language,
                        PlaceTranslation.class, "placeId", "title");
                return matched;
            });
        }
        PageRequest paging = PageRequests.of(params.page(), params.size(),
                ListFilters.sort(params.sort(), Set.of("sortOrder", "slug")));
        Page<Place> result = places.findAll(spec, paging);
        List<UUID> ids = result.getContent().stream().map(Place::getId).toList();
        Map<UUID, List<PlaceTranslation>> byPlace = ids.isEmpty() ? Map.of()
                : translations.findByProjectIdAndPlaceIdIn(projectId, ids).stream()
                        .collect(Collectors.groupingBy(PlaceTranslation::getPlaceId));
        return result.map(place -> new PlaceView(place, byPlace.getOrDefault(place.getId(), List.of())));
    }
}
