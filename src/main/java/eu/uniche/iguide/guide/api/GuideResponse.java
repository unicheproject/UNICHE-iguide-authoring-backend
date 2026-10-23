package eu.uniche.iguide.guide.api;

import eu.uniche.iguide.asset.api.AssetResponse;
import eu.uniche.iguide.guide.application.BlockView;
import eu.uniche.iguide.guide.application.GuideView;
import eu.uniche.iguide.guide.domain.Guide;
import eu.uniche.iguide.guide.domain.GuideBlockTranslation;
import eu.uniche.iguide.guide.domain.GuideBlockType;
import eu.uniche.iguide.guide.domain.GuideTargetType;
import eu.uniche.iguide.guide.domain.GuideTranslation;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A Guide with its translations and its blocks in order. {@code hasWorkingCopy} and {@code workingCopyUpdatedAt}
 * tell whether the Guide has pending changes ({@code 07} section 14).
 */
public record GuideResponse(UUID id, UUID projectId, String slug, ResourceStatus status, String internalName,
                            Integer estimatedReadingMinutes, boolean featured, int sortOrder, Instant archivedAt,
                            Instant createdAt, Instant updatedAt, long version, boolean hasWorkingCopy,
                            Instant workingCopyUpdatedAt, Map<String, GuideRequests.GuideTranslationBody> translations,
                            List<Block> blocks, List<AssetResponse.Binding> media) {

    /** A block at its 0-based {@code sortOrder}. */
    public record Block(UUID id, GuideBlockType blockType, GuideTargetType resourceType, UUID resourceId,
                        int sortOrder, Map<String, GuideRequests.BlockTranslationBody> translations) {

        public static Block from(BlockView view) {
            Map<String, GuideRequests.BlockTranslationBody> texts = view.translations().stream()
                    .collect(Collectors.toMap(GuideBlockTranslation::getLanguageCode,
                            t -> new GuideRequests.BlockTranslationBody(t.getText(), t.getCaption()),
                            (a, b) -> a, TreeMap::new));
            var b = view.block();
            return new Block(b.getId(), b.getBlockType(), b.getResourceType(), b.getResourceId(), b.getSortOrder(), texts);
        }
    }

    public static GuideResponse from(GuideView view) {
        return from(view, null);
    }

    public static GuideResponse from(GuideView view, Instant workingCopyUpdatedAt) {
        Guide g = view.guide();
        Map<String, GuideRequests.GuideTranslationBody> texts = view.translations().stream()
                .collect(Collectors.toMap(GuideTranslation::getLanguageCode,
                        t -> new GuideRequests.GuideTranslationBody(t.getTitle(), t.getSummary()),
                        (a, b) -> a, TreeMap::new));
        return new GuideResponse(g.getId(), g.getProjectId(), g.getSlug(), g.getStatus(), g.getInternalName(),
                g.getEstimatedReadingMinutes(), g.isFeatured(), g.getSortOrder(), g.getArchivedAt(), g.getCreatedAt(),
                g.getUpdatedAt(), g.getVersion(), workingCopyUpdatedAt != null, workingCopyUpdatedAt, texts,
                view.blocks().stream().map(Block::from).toList(),
                view.media().stream().map(AssetResponse.Binding::from).toList());
    }
}
