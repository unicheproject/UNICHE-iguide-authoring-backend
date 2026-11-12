package eu.uniche.iguide.guide.application;

import eu.uniche.iguide.guide.domain.GuideBlock;
import eu.uniche.iguide.guide.domain.GuideBlockType;
import eu.uniche.iguide.guide.domain.GuideTargetType;
import eu.uniche.iguide.guide.infrastructure.GuideBlockRepository;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ErrorPaths;
import eu.uniche.iguide.resource.application.ResourceRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The rules and row writes of a Guide's blocks, shared by the block endpoints and the apply of a working copy.
 * Every method runs inside the caller's transaction, without a status check.
 */
@Component
public class GuideBlockWriter {

    private final GuideRules rules;
    private final GuideBlockRepository blocks;

    public GuideBlockWriter(GuideRules rules, GuideBlockRepository blocks) {
        this.rules = rules;
        this.blocks = blocks;
    }

    /**
     * Makes the Guide's blocks exactly {@code wanted}, in that order, inside the caller's transaction and
     * without a status check: the apply of a working copy. Blocks not wanted are removed, kept ones updated,
     * the rest created; then the INLINE media rows follow the text. Errors are reported at {@code blocks[i]}.
     * The Guide itself is touched by the caller's save of its own fields.
     */
    public void replaceBlocks(UUID projectId, UUID guideId, List<GuideBlockUseCase.WantedBlock> wanted) {
        List<GuideBlockUseCase.WantedBlock> desired = wanted == null ? List.of() : wanted;
        Map<UUID, GuideBlock> current = rules.blocksOf(projectId, guideId).stream()
                .collect(Collectors.toMap(GuideBlock::getId, b -> b));
        Set<UUID> kept = desired.stream().map(GuideBlockUseCase.WantedBlock::id).filter(current::containsKey)
                .collect(Collectors.toSet());
        List<GuideBlock> removed = current.values().stream().filter(b -> !kept.contains(b.getId())).toList();
        blocks.deleteAll(removed);
        blocks.flush();

        List<GuideBlock> ordered = new ArrayList<>();
        Set<UUID> used = new HashSet<>();
        for (int i = 0; i < desired.size(); i++) {
            GuideBlockUseCase.WantedBlock item = desired.get(i);
            int position = i;
            GuideBlock existing = item.id() != null && used.add(item.id()) ? current.get(item.id()) : null;
            if (existing != null && kept.contains(existing.getId())) {
                ordered.add(ErrorPaths.within("blocks[" + i + "]", () -> {
                    if (item.blockType() != null && item.blockType() != existing.getBlockType()) {
                        throw new ValidationFailedException("A block keeps its type.", List.of(new ErrorItem(
                                "blockType", "IMMUTABLE", "Remove the block and add one of the other type.")));
                    }
                    return updateIn(projectId, existing, item.resourceType(), item.resourceId(), item.translations());
                }));
            } else {
                ordered.add(ErrorPaths.within("blocks[" + i + "]", () -> addIn(projectId, guideId, item.blockType(),
                        item.resourceType(), item.resourceId(), item.translations(), position)));
            }
        }
        renumber(ordered);
        rules.reconcileInlineAssets(projectId, guideId);
    }

    GuideBlock addIn(UUID projectId, UUID guideId, GuideBlockType type, GuideTargetType targetType,
                             UUID targetId, Map<String, BlockTranslationInput> translations, int position) {
        List<ErrorItem> errors = new ArrayList<>();
        rules.checkBlock(projectId, type, targetType, targetId, errors);
        Map<String, BlockTranslationInput> normalized = normalize(projectId, type, translations, errors);
        ResourceRules.throwIfAny(errors, "The block is invalid.");
        GuideBlock block = blocks.saveAndFlush(GuideBlock.of(projectId, guideId, type, targetType, targetId, position));
        rules.saveBlockTranslations(block, normalized);
        return block;
    }

    GuideBlock updateIn(UUID projectId, GuideBlock block, GuideTargetType targetType, UUID targetId,
                                Map<String, BlockTranslationInput> translations) {
        List<ErrorItem> errors = new ArrayList<>();
        rules.checkBlock(projectId, block.getBlockType(), targetType, targetId, errors);
        Map<String, BlockTranslationInput> normalized = normalize(projectId, block.getBlockType(), translations, errors);
        ResourceRules.throwIfAny(errors, "The block is invalid.");
        block.target(targetType, targetId);
        blocks.saveAndFlush(block);
        rules.saveBlockTranslations(block, normalized);
        return block;
    }

    /** Positions 0 to n-1 in the given order; the order constraint is deferred to the commit. */
    void renumber(List<GuideBlock> inOrder) {
        for (int i = 0; i < inOrder.size(); i++) {
            inOrder.get(i).moveTo(i);
        }
        blocks.saveAllAndFlush(inOrder);
    }

    private Map<String, BlockTranslationInput> normalize(UUID projectId, GuideBlockType type,
                                                         Map<String, BlockTranslationInput> translations,
                                                         List<ErrorItem> errors) {
        Map<String, BlockTranslationInput> requested = translations == null ? Map.of() : translations;
        rules.checkLanguages(projectId, requested.keySet(), errors);
        Map<String, BlockTranslationInput> normalized = new LinkedHashMap<>();
        if (type != null) {
            requested.forEach((code, input) -> normalized.put(code, rules.normalize(projectId, type, code,
                    input == null ? new BlockTranslationInput(null, null) : input, errors)));
        }
        return normalized;
    }
}
