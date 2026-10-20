package eu.uniche.iguide.asset.application;

import eu.uniche.iguide.asset.domain.Asset;
import eu.uniche.iguide.asset.domain.AssetTranslation;
import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * An Asset with its translations, a signed URL that reads its bytes until {@code urlExpiresAt}, and the
 * number of Resources and Floor Plans that use it.
 */
public record AssetView(Asset asset, List<AssetTranslation> translations, URI url, Instant urlExpiresAt,
                        long usageCount) {
}
