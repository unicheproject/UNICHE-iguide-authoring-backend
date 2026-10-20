package eu.uniche.iguide.place.application;

import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.domain.PlaceTranslation;
import eu.uniche.iguide.place.infrastructure.PlaceRepository;
import eu.uniche.iguide.place.infrastructure.PlaceTranslationRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
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
 * Pages through a Project's Places in {@code sortOrder} ({@code 06} section 16), filtered by status and
 * by {@code q}, a case-insensitive match on the title in any language ({@code 07} section 14).
 */
@Service
public class ListPlacesQuery {

    private final ProjectAccessGateway accessGateway;
    private final PlaceRepository places;
    private final PlaceTranslationRepository translations;

    public ListPlacesQuery(ProjectAccessGateway accessGateway, PlaceRepository places,
                           PlaceTranslationRepository translations) {
        this.accessGateway = accessGateway;
        this.places = places;
        this.translations = translations;
    }

    public Page<PlaceView> execute(UUID projectId, UnichePrincipal principal, ResourceStatus status, String q,
                                   Integer page, Integer size) {
        accessGateway.requireAccess(projectId, principal);
        Specification<Place> spec = (root, query, cb) -> cb.equal(root.get("projectId"), projectId);
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.strip().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            spec = spec.and((root, query, cb) -> {
                Subquery<UUID> titled = query.subquery(UUID.class);
                var t = titled.from(PlaceTranslation.class);
                titled.select(t.get("placeId")).where(
                        cb.equal(t.get("projectId"), projectId),
                        cb.equal(t.get("placeId"), root.get("id")),
                        cb.like(cb.lower(t.get("title")), pattern, '\\'));
                return cb.exists(titled);
            });
        }
        Page<Place> result = places.findAll(spec,
                PageRequests.of(page, size, Sort.by("sortOrder", "createdAt")));
        List<UUID> ids = result.getContent().stream().map(Place::getId).toList();
        Map<UUID, List<PlaceTranslation>> byPlace = ids.isEmpty() ? Map.of()
                : translations.findByProjectIdAndPlaceIdIn(projectId, ids).stream()
                        .collect(Collectors.groupingBy(PlaceTranslation::getPlaceId));
        return result.map(place -> new PlaceView(place, byPlace.getOrDefault(place.getId(), List.of())));
    }
}
