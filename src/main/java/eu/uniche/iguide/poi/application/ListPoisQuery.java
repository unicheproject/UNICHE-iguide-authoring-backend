package eu.uniche.iguide.poi.application;

import eu.uniche.iguide.asset.domain.ResourceAsset;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.poi.domain.PoiTranslation;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import eu.uniche.iguide.poi.infrastructure.PoiTranslationRepository;
import eu.uniche.iguide.poi.infrastructure.PointOfInterestRepository;
import eu.uniche.iguide.resource.application.PageRequests;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import jakarta.persistence.criteria.Subquery;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * Pages through a Project's Points of Interest in {@code sortOrder} ({@code 06} section 16), filtered by
 * status, by Place, and by {@code q}: a case-insensitive match on the title in any language, or the exact
 * exhibit code ({@code 07} section 14, {@code 13} section 3).
 */
@Service
public class ListPoisQuery {

    private final ProjectAccessGateway accessGateway;
    private final PointOfInterestRepository pois;
    private final PoiTranslationRepository translations;
    private final PoiRules rules;

    public ListPoisQuery(ProjectAccessGateway accessGateway, PointOfInterestRepository pois,
                         PoiTranslationRepository translations, PoiRules rules) {
        this.accessGateway = accessGateway;
        this.pois = pois;
        this.translations = translations;
        this.rules = rules;
    }

    public Page<PoiView> execute(UUID projectId, UnichePrincipal principal, ResourceStatus status, UUID placeId,
                                 String q, Integer page, Integer size) {
        accessGateway.requireAccess(projectId, principal);
        Specification<PointOfInterest> spec = (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (placeId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("placeId"), placeId));
        }
        if (q != null && !q.isBlank()) {
            String term = q.strip();
            String pattern = "%" + term.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> {
                Subquery<UUID> titled = query.subquery(UUID.class);
                var t = titled.from(PoiTranslation.class);
                titled.select(t.get("poiId")).where(
                        cb.equal(t.get("projectId"), projectId),
                        cb.equal(t.get("poiId"), root.get("id")),
                        cb.like(cb.lower(t.get("title")), pattern, '\\'));
                return cb.or(cb.equal(root.get("code"), term), cb.exists(titled));
            });
        }
        Page<PointOfInterest> result = pois.findAll(spec,
                PageRequests.of(page, size, Sort.by("sortOrder", "createdAt")));
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
