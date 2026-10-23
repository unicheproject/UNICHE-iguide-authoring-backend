package eu.uniche.iguide.place.api;

import eu.uniche.iguide.place.application.PlaceView;
import eu.uniche.iguide.place.domain.Place;
import eu.uniche.iguide.place.domain.PlaceEnvironment;
import eu.uniche.iguide.place.domain.PlaceTranslation;
import eu.uniche.iguide.place.domain.PlaceType;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A Place with its translations keyed by language code. {@code hasWorkingCopy} and {@code workingCopyUpdatedAt}
 * tell whether the Place has pending changes ({@code 07} section 14).
 */
public record PlaceResponse(UUID id, UUID projectId, UUID parentPlaceId, String slug, PlaceType type,
                            PlaceEnvironment environment, ResourceStatus status, String internalName,
                            BigDecimal latitude, BigDecimal longitude, boolean featured, int sortOrder,
                            Instant archivedAt, Instant createdAt, Instant updatedAt, long version,
                            boolean hasWorkingCopy, Instant workingCopyUpdatedAt,
                            Map<String, PlaceTranslationBody> translations) {

    public static PlaceResponse from(PlaceView view) {
        return from(view, null);
    }

    public static PlaceResponse from(PlaceView view, Instant workingCopyUpdatedAt) {
        Place p = view.place();
        Map<String, PlaceTranslationBody> translations = view.translations().stream()
                .collect(Collectors.toMap(PlaceTranslation::getLanguageCode, PlaceTranslationBody::from,
                        (a, b) -> a, TreeMap::new));
        return new PlaceResponse(p.getId(), p.getProjectId(), p.getParentPlaceId(), p.getSlug(), p.getType(),
                p.getEnvironment(), p.getStatus(), p.getInternalName(), p.getLatitude(), p.getLongitude(),
                p.isFeatured(), p.getSortOrder(), p.getArchivedAt(), p.getCreatedAt(), p.getUpdatedAt(),
                p.getVersion(), workingCopyUpdatedAt != null, workingCopyUpdatedAt, translations);
    }
}
