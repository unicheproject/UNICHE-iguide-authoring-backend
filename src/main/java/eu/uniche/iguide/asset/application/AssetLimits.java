package eu.uniche.iguide.asset.application;

import eu.uniche.iguide.asset.domain.AssetType;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Upload size limits per Asset type ({@code 10} section 3). The global limit caps every per-type one; a
 * per-type limit above it stops the application at startup.
 */
@Component
public class AssetLimits {

    private final long maxUploadBytes;
    private final Map<AssetType, Long> perType = new EnumMap<>(AssetType.class);

    public AssetLimits(@Value("${iguide.assets.max-upload-bytes}") long maxUploadBytes,
                       @Value("${iguide.assets.max-size.image}") long image,
                       @Value("${iguide.assets.max-size.audio}") long audio,
                       @Value("${iguide.assets.max-size.video}") long video,
                       @Value("${iguide.assets.max-size.document}") long document,
                       @Value("${iguide.assets.max-size.three-d-model}") long threeDModel,
                       @Value("${iguide.assets.max-size.other}") long other) {
        this.maxUploadBytes = maxUploadBytes;
        perType.put(AssetType.IMAGE, image);
        perType.put(AssetType.FLOOR_PLAN, image);
        perType.put(AssetType.AUDIO, audio);
        perType.put(AssetType.VIDEO, video);
        perType.put(AssetType.DOCUMENT, document);
        perType.put(AssetType.THREE_D_MODEL, threeDModel);
        perType.put(AssetType.OTHER, other);
        perType.forEach((type, limit) -> {
            if (limit <= 0 || limit > maxUploadBytes) {
                throw new IllegalStateException("The upload limit for " + type
                        + " must be positive and at most iguide.assets.max-upload-bytes.");
            }
        });
    }

    public long limitFor(AssetType type) {
        return perType.get(type);
    }

    public Map<AssetType, Long> perType() {
        return new EnumMap<>(perType);
    }

    public long maxUploadBytes() {
        return maxUploadBytes;
    }
}
