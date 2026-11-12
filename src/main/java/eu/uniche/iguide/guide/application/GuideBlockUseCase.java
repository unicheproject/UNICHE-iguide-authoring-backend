package eu.uniche.iguide.guide.application;

import eu.uniche.iguide.guide.domain.Guide;
import eu.uniche.iguide.guide.domain.GuideBlock;
import eu.uniche.iguide.guide.domain.GuideBlockTranslation;
import eu.uniche.iguide.guide.domain.GuideBlockType;
import eu.uniche.iguide.guide.domain.GuideTargetType;
import eu.uniche.iguide.guide.infrastructure.GuideBlockRepository;
import eu.uniche.iguide.guide.infrastructure.GuideRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ResourceRules;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.workingcopy.application.PatchWorkingCopyUseCase;
import eu.uniche.iguide.workingcopy.application.PatchWorkingCopyUseCase.Patched;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The blocks of a Guide, a child collection ({@code 07} section 14: {@code .../guides/{id}/blocks},
 * {@code .../blocks/{blockId}}, {@code .../blocks:order}). Positions stay 0-based and contiguous: a new
 * block goes last, removing one closes the gap. A block keeps its type; its target may change. On a DRAFT Guide
 * the rows are written; on an ACTIVE one the change goes into the Guide's working copy, started from the live
 * Guide when it has none, and visitors see it after apply (DECISIONS.md D-045). The answer has the same shape
 * either way. An ARCHIVED Guide is refused.
 */
@Service
public class GuideBlockUseCase {

    private static final String BLOCKS = "blocks";

    private final ProjectAccessGateway accessGateway;
    private final GuideRules rules;
    private final GuideBlockWriter writer;
    private final GuideRepository guides;
    private final GuideBlockRepository blocks;
    private final PatchWorkingCopyUseCase patch;
    private final TransactionTemplate transactionTemplate;

    public GuideBlockUseCase(ProjectAccessGateway accessGateway, GuideRules rules, GuideBlockWriter writer,
                             GuideRepository guides, GuideBlockRepository blocks, PatchWorkingCopyUseCase patch,
                             TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.writer = writer;
        this.guides = guides;
        this.blocks = blocks;
        this.patch = patch;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * One block of a working-copy payload. {@code id} names an existing block of the Guide to keep; an absent
     * or unknown id creates a block. A kept block keeps its type: a different {@code blockType} is an error.
     */
    public record WantedBlock(UUID id, GuideBlockType blockType, GuideTargetType resourceType, UUID resourceId,
                              Map<String, BlockTranslationInput> translations) {
    }

    /**
     * A block as the endpoints answer it: a row of a DRAFT Guide, or an entry of an ACTIVE Guide's working copy,
     * whose id there becomes a row's at apply only for blocks that already existed.
     */
    public record BlockData(UUID id, GuideBlockType blockType, GuideTargetType resourceType, UUID resourceId,
                            int sortOrder, Map<String, BlockTranslationInput> translations) {

        static BlockData of(BlockView view) {
            Map<String, BlockTranslationInput> texts = new HashMap<>();
            for (GuideBlockTranslation t : view.translations()) {
                texts.put(t.getLanguageCode(), new BlockTranslationInput(t.getText(), t.getCaption()));
            }
            GuideBlock b = view.block();
            return new BlockData(b.getId(), b.getBlockType(), b.getResourceType(), b.getResourceId(),
                    b.getSortOrder(), texts);
        }

        static BlockData of(WantedBlock block, int sortOrder) {
            return new BlockData(block.id(), block.blockType(), block.resourceType(), block.resourceId(), sortOrder,
                    block.translations() == null ? Map.of() : block.translations());
        }
    }

    public BlockData add(UUID projectId, UUID guideId, UnichePrincipal principal, GuideBlockType type,
                         GuideTargetType targetType, UUID targetId, Map<String, BlockTranslationInput> translations) {
        accessGateway.requireAccess(projectId, principal);
        BlockData added = transactionTemplate.execute(status -> {
            Guide guide = draftOrNull(projectId, guideId);
            if (guide == null) {
                return null;
            }
            GuideBlock block = writer.addIn(projectId, guideId, type, targetType, targetId, translations,
                    rules.blocksOf(projectId, guideId).size());
            rules.reconcileInlineAssets(projectId, guideId);
            touch(guide, principal);
            return BlockData.of(rules.blockView(block));
        });
        if (added != null) {
            return added;
        }
        return patch.execute(projectId, WorkingCopyResourceType.GUIDE, guideId, principal, payload -> {
            List<Map<String, Object>> items = PatchWorkingCopyUseCase.children(payload, BLOCKS);
            WantedBlock block = new WantedBlock(UUID.randomUUID(), type, targetType, targetId,
                    translations == null ? Map.of() : translations);
            items.add(patch.entry(block));
            int position = items.size() - 1;
            return new Patched<>(BlockData.of(block, position), at(position));
        });
    }

    public BlockData update(UUID projectId, UUID guideId, UUID blockId, UnichePrincipal principal,
                            GuideTargetType targetType, UUID targetId, Map<String, BlockTranslationInput> translations) {
        accessGateway.requireAccess(projectId, principal);
        BlockData updated = transactionTemplate.execute(status -> {
            Guide guide = draftOrNull(projectId, guideId);
            if (guide == null) {
                return null;
            }
            GuideBlock block = rules.requireBlock(projectId, guideId, blockId);
            writer.updateIn(projectId, block, targetType, targetId, translations);
            rules.reconcileInlineAssets(projectId, guideId);
            touch(guide, principal);
            return BlockData.of(rules.blockView(block));
        });
        if (updated != null) {
            return updated;
        }
        return patch.execute(projectId, WorkingCopyResourceType.GUIDE, guideId, principal, payload -> {
            List<Map<String, Object>> items = PatchWorkingCopyUseCase.children(payload, BLOCKS);
            int position = items.indexOf(pending(items, blockId));
            WantedBlock current = patch.read(items.get(position), WantedBlock.class);
            // Languages not sent keep their texts, as on a DRAFT Guide.
            Map<String, BlockTranslationInput> texts = new HashMap<>(
                    current.translations() == null ? Map.of() : current.translations());
            if (translations != null) {
                texts.putAll(translations);
            }
            WantedBlock block = new WantedBlock(blockId, current.blockType(), targetType, targetId, texts);
            items.set(position, patch.entry(block));
            return new Patched<>(BlockData.of(block, position), at(position));
        });
    }

    public void remove(UUID projectId, UUID guideId, UUID blockId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        boolean removed = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            Guide guide = draftOrNull(projectId, guideId);
            if (guide == null) {
                return false;
            }
            blocks.delete(rules.requireBlock(projectId, guideId, blockId));
            blocks.flush();
            writer.renumber(rules.blocksOf(projectId, guideId));
            rules.reconcileInlineAssets(projectId, guideId);
            touch(guide, principal);
            return true;
        }));
        if (!removed) {
            patch.execute(projectId, WorkingCopyResourceType.GUIDE, guideId, principal, payload -> {
                List<Map<String, Object>> items = PatchWorkingCopyUseCase.children(payload, BLOCKS);
                items.remove(pending(items, blockId));
                return new Patched<>(true, BLOCKS);
            });
        }
    }

    /** {@code PUT .../blocks:order}: every block id of the Guide, in the new order. */
    public List<BlockData> reorder(UUID projectId, UUID guideId, UnichePrincipal principal, List<UUID> orderedIds) {
        accessGateway.requireAccess(projectId, principal);
        List<UUID> ids = orderedIds == null ? List.of() : orderedIds;
        List<BlockData> reordered = transactionTemplate.execute(status -> {
            Guide guide = draftOrNull(projectId, guideId);
            if (guide == null) {
                return null;
            }
            List<GuideBlock> current = rules.blocksOf(projectId, guideId);
            Map<UUID, GuideBlock> byId = current.stream().collect(Collectors.toMap(GuideBlock::getId, b -> b));
            requireEvery(ids, byId.keySet().stream().toList());
            for (int i = 0; i < ids.size(); i++) {
                byId.get(ids.get(i)).moveTo(i);
            }
            blocks.saveAllAndFlush(current);
            touch(guide, principal);
            return rules.view(guide).blocks().stream().map(BlockData::of).toList();
        });
        if (reordered != null) {
            return reordered;
        }
        return patch.execute(projectId, WorkingCopyResourceType.GUIDE, guideId, principal, payload -> {
            List<Map<String, Object>> items = PatchWorkingCopyUseCase.children(payload, BLOCKS);
            requireEvery(ids, items.stream().map(item -> UUID.fromString(String.valueOf(item.get("id")))).toList());
            List<Map<String, Object>> ordered = ids.stream()
                    .map(id -> PatchWorkingCopyUseCase.child(items, id).orElseThrow()).toList();
            items.clear();
            items.addAll(ordered);
            List<BlockData> result = new ArrayList<>();
            for (int i = 0; i < ordered.size(); i++) {
                result.add(BlockData.of(patch.read(ordered.get(i), WantedBlock.class), i));
            }
            return new Patched<>(result, BLOCKS);
        });
    }

    private static void requireEvery(List<UUID> ids, List<UUID> existing) {
        if (ids.size() != existing.size() || !new HashSet<>(ids).equals(new HashSet<>(existing))) {
            throw new ValidationFailedException("The order must list every block of the guide exactly once.",
                    List.of(new ErrorItem("ids", "INCOMPLETE_ORDER", "List every block of this guide exactly once.")));
        }
    }

    /** The Guide when it is a DRAFT, {@code null} when it is ACTIVE; an ARCHIVED one is refused. */
    private Guide draftOrNull(UUID projectId, UUID guideId) {
        Guide guide = rules.requireGuide(projectId, guideId);
        if (guide.getStatus() == ResourceStatus.ACTIVE) {
            return null;
        }
        ResourceRules.requireDraft(guide.getStatus(), "guide");
        return guide;
    }

    private static Map<String, Object> pending(List<Map<String, Object>> items, UUID blockId) {
        return PatchWorkingCopyUseCase.child(items, blockId)
                .orElseThrow(() -> new ResourceNotFoundException("No such block in this guide."));
    }

    private static String at(int position) {
        return BLOCKS + "[" + position + "]";
    }

    private void touch(Guide guide, UnichePrincipal principal) {
        guide.touch(principal.subject(), Instant.now());
        guides.saveAndFlush(guide);
    }
}
