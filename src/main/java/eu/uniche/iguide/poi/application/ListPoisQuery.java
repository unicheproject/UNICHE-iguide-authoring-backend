package eu.uniche.iguide.poi.application;

import eu.uniche.iguide.asset.domain.ResourceAsset;
import eu.uniche.iguide.category.application.CategoryFilter;
import eu.uniche.iguide.category.domain.CategoryScope;
import eu.uniche.iguide.language.application.ProjectLanguageLookup;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.poi.domain.PoiTranslation;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import eu.uniche.iguide.poi.infrastructure.PoiTranslationRepository;
import eu.uniche.iguide.poi.infrastructure.PointOfInterestRepository;
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
 * Pages through a Project's Points of Interest in {@code sortOrder} ({@code 06} section 16), filtered by
 * status, by Place, and by {@code q}: a case-insensitive match on the title in any language, or the exact
 * exhibit code, and by {@code pending}, the ones with a working copy ({@code 07} section 14, {@code 13}
 * section 3).
 *
 * <p>{@code language} narrows {@code q} to one language, {@code sort} names an indexed column, and the other
 * filters of {@link eu.uniche.iguide.resource.application.ListParams} apply (DECISIONS.md D-030).
 */
@Service
public class ListPoisQuery {

    private final ProjectAccessGateway accessGateway;
    private final ProjectLanguageLookup languages;
    private final PointOfInterestRepository pois;
    private final PoiTranslationRepository translations;
    private final PoiRules rules;

    public ListPoisQuery(ProjectAccessGateway accessGateway, ProjectLanguageLookup languages,
                         PointOfInterestRepository pois,
                         PoiTranslationRepository translations, PoiRules rules) {
        this.accessGateway = accessGateway;
        this.languages = languages;
        this.pois = pois;
        this.translations = translations;
        this.rules = rules;
    }

    public Page<PoiView> execute(UUID projectId, UnichePrincipal principal, UUID placeId, String categoryId,
                                 ListParams params) {
        accessGateway.requireAccess(projectId, principal);
        String language = ListFilters.requireLanguage(params.language(), languages.enabledCodes(projectId));
        Specification<PointOfInterest> spec = (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
        if (params.status() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), params.status()));
        }
        if (placeId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("placeId"), placeId));
        }
        if (params.featured() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("featured"), params.featured()));
        }
        Specification<PointOfInterest> inCategory = CategoryFilter.of(projectId, categoryId, CategoryScope.POI);
        if (inCategory != null) {
            spec = spec.and(inCategory);
        }
        if (params.pending()) {
            spec = spec.and(WorkingCopyStore.pending(projectId, WorkingCopyResourceType.POI));
        }
        String pattern = ListFilters.likePattern(params.q());
        if (pattern != null) {
            String term = params.q().strip();
            spec = spec.and((root, query, cb) -> {
                var matched = ListFilters.translationMatches(root, query, cb, projectId, pattern, language,
                        PoiTranslation.class, "poiId", "title");
                return cb.or(cb.equal(root.get("code"), term), matched);
            });
        }
        PageRequest paging = PageRequests.of(params.page(), params.size(),
                ListFilters.sort(params.sort(), Set.of("sortOrder", "slug")));
        Page<PointOfInterest> result = pois.findAll(spec, paging);
        List<UUID> ids = result.getContent().stream().map(PointOfInterest::getId).toList();
        Map<UUID, List<PoiTranslation>> byPoi = ids.isEmpty() ? Map.of()
                : translations.findByProjectIdAndPoiIdIn(projectId, ids).stream()
                        .collect(Collectors.groupingBy(PoiTranslation::getPoiId));
        Map<UUID, List<UUID>> categoryIds = rules.categoryIds(projectId, ids);
        Map<UUID, List<ResourceAsset>> media = rules.media(projectId, ids);
        return result.map(poi -> new PoiView(poi, byPoi.getOrDefault(poi.getId(), List.of()),
                categoryIds.getOrDefault(poi.getId(), List.of()), media.getOrDefault(poi.getId(), List.of())));
    }
}
