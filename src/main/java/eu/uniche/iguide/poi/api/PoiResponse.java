package eu.uniche.iguide.poi.api;

import eu.uniche.iguide.asset.api.AssetResponse;
import eu.uniche.iguide.poi.application.PoiView;
import eu.uniche.iguide.poi.domain.PoiTranslation;
import eu.uniche.iguide.poi.domain.PointOfInterest;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A Point of Interest with its translations keyed by language code and the ids of its Categories (at most
 * one, {@code 11} section 3 carries them as {@code categoryIds}). {@code hasWorkingCopy} and
 * {@code workingCopyUpdatedAt} tell whether it has pending changes ({@code 07} section 14).
 */
public record PoiResponse(UUID id, UUID projectId, UUID placeId, String slug, String code, ResourceStatus status,
                          String internalName, BigDecimal latitude, BigDecimal longitude,
                          Integer estimatedVisitMinutes, boolean featured, int sortOrder, Instant archivedAt,
                          Instant createdAt, Instant updatedAt, long version, boolean hasWorkingCopy,
                          Instant workingCopyUpdatedAt, Map<String, PoiTranslationBody> translations,
                          List<UUID> categoryIds, List<AssetResponse.Binding> media) {

    public static PoiResponse from(PoiView view) {
        return from(view, null);
    }

    public static PoiResponse from(PoiView view, Instant workingCopyUpdatedAt) {
        PointOfInterest p = view.poi();
        Map<String, PoiTranslationBody> translations = view.translations().stream()
                .collect(Collectors.toMap(PoiTranslation::getLanguageCode, PoiTranslationBody::from,
                        (a, b) -> a, TreeMap::new));
        return new PoiResponse(p.getId(), p.getProjectId(), p.getPlaceId(), p.getSlug(), p.getCode(), p.getStatus(),
                p.getInternalName(), p.getLatitude(), p.getLongitude(), p.getEstimatedVisitMinutes(), p.isFeatured(),
                p.getSortOrder(), p.getArchivedAt(), p.getCreatedAt(), p.getUpdatedAt(), p.getVersion(),
                workingCopyUpdatedAt != null, workingCopyUpdatedAt, translations, view.categoryIds(), view.media().stream().map(AssetResponse.Binding::from).toList());
    }
}
