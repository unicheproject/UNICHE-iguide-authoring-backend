package eu.uniche.iguide.asset.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * The role of an Asset on a Resource ({@code 03} section 4, ResourceAsset). The spec lists example usages;
 * {@code VIDEO} is added for the CMS video slot on a Point of Interest (DECISIONS.md D-025). {@code INLINE}
 * rows are materialized from rich text and never written directly.
 */
public enum AssetUsage {
    HERO_IMAGE(true, EnumSet.of(AssetType.IMAGE)),
    THUMBNAIL(true, EnumSet.of(AssetType.IMAGE)),
    GALLERY_IMAGE(false, EnumSet.of(AssetType.IMAGE)),
    VIDEO(true, EnumSet.of(AssetType.VIDEO)),
    AUDIO_NARRATION(true, EnumSet.of(AssetType.AUDIO)),
    ATTACHMENT(false, EnumSet.of(AssetType.DOCUMENT, AssetType.OTHER)),
    MODEL(true, EnumSet.of(AssetType.THREE_D_MODEL)),
    MODEL_POSTER(true, EnumSet.of(AssetType.IMAGE)),
    INLINE(false, EnumSet.of(AssetType.IMAGE));

    private final boolean single;
    private final Set<AssetType> accepted;

    AssetUsage(boolean single, Set<AssetType> accepted) {
        this.single = single;
        this.accepted = accepted;
    }

    /** At most one Asset per Resource and language in this role. */
    public boolean isSingle() {
        return single;
    }

    public boolean accepts(AssetType type) {
        return accepted.contains(type);
    }

    public Set<AssetType> accepted() {
        return Set.copyOf(accepted);
    }
}
