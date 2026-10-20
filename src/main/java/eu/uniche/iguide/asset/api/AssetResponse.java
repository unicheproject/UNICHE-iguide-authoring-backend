package eu.uniche.iguide.asset.api;

import eu.uniche.iguide.asset.application.AssetRules;
import eu.uniche.iguide.asset.application.AssetView;
import eu.uniche.iguide.asset.domain.Asset;
import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.asset.domain.AssetTranslation;
import eu.uniche.iguide.asset.domain.AssetType;
import eu.uniche.iguide.asset.domain.AssetUsage;
import eu.uniche.iguide.asset.domain.ResourceAsset;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An Asset with its translations keyed by language code. {@code url} reads the bytes until
 * {@code urlExpiresAt} ({@code 07} section 10: a short-lived signed URL; the bucket stays private).
 * {@code usageCount} counts the Resources and Floor Plans that use it. {@code hasWorkingCopy} is always false
 * until working copies exist (GAPS.md G-027).
 */
public record AssetResponse(UUID id, UUID projectId, AssetType type, ResourceStatus status, String internalName,
                            boolean decorative, String originalFilename, String mimeType, long sizeBytes,
                            String checksum, Integer width, Integer height, BigDecimal durationSeconds,
                            Instant archivedAt, Instant createdAt, Instant updatedAt, long version,
                            boolean hasWorkingCopy, URI url, Instant urlExpiresAt, long usageCount,
                            Map<String, AssetRequests.TranslationBody> translations) {

    public static AssetResponse from(AssetView view) {
        Asset a = view.asset();
        Map<String, AssetRequests.TranslationBody> texts = view.translations().stream()
                .collect(Collectors.toMap(AssetTranslation::getLanguageCode,
                        t -> new AssetRequests.TranslationBody(t.getTitle(), t.getCaption(), t.getAlternativeText(),
                                t.getCredits(), t.getTranscript()), (x, y) -> x, TreeMap::new));
        return new AssetResponse(a.getId(), a.getProjectId(), a.getType(), a.getStatus(), a.getInternalName(),
                a.isDecorative(), a.getOriginalFilename(), a.getMimeType(), a.getSizeBytes(), a.getChecksum(),
                a.getWidth(), a.getHeight(), a.getDurationSeconds(), a.getArchivedAt(), a.getCreatedAt(),
                a.getUpdatedAt(), a.getVersion(), false, view.url(), view.urlExpiresAt(), view.usageCount(), texts);
    }

    /** One media binding of a Resource, at its {@code sortOrder} among the Resource's bindings. */
    public record Binding(UUID id, AssetResourceType resourceType, UUID resourceId, UUID assetId, AssetUsage usage,
                          String languageCode, int sortOrder) {

        public static Binding from(ResourceAsset b) {
            return new Binding(b.getId(), b.getResourceType(), b.getResourceId(), b.getAssetId(), b.getUsage(),
                    b.getLanguageCode(), b.getSortOrder());
        }
    }

    /** One Resource or Floor Plan that uses an Asset, with its roles there. */
    public record Usage(ResourceReference resource, List<AssetRules.Usage> usages) {

        public static Usage from(AssetRules.ResourceUsage usage) {
            return new Usage(usage.resource(), usage.usages());
        }
    }

    /** A short-lived link to the file. */
    public record Link(URI url, Instant expiresAt) {
    }

    /** {@code GET /api/v1/upload-limits}: the global and per-type limits in bytes ({@code 10} section 3). */
    public record UploadLimits(long maxUploadBytes, Map<AssetType, Long> perType) {
    }
}
