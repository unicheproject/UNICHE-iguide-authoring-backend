package eu.uniche.iguide.guide.application;

import eu.uniche.iguide.guide.domain.Guide;
import eu.uniche.iguide.guide.domain.GuideBlock;
import eu.uniche.iguide.guide.domain.GuideBlockType;
import eu.uniche.iguide.guide.domain.GuideTargetType;
import eu.uniche.iguide.guide.infrastructure.GuideBlockRepository;
import eu.uniche.iguide.guide.infrastructure.GuideRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ErrorPaths;
import eu.uniche.iguide.resource.application.ResourceRules;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The blocks of a DRAFT Guide, a child collection ({@code 07} section 14: {@code .../guides/{id}/blocks},
 * {@code .../blocks/{blockId}}, {@code .../blocks:order}). Positions stay 0-based and contiguous: a new
 * block goes last, removing one closes the gap. A block keeps its type; its target may change. An ACTIVE
 * Guide changes its blocks through its working copy, whose apply calls {@link #replaceBlocks}
 * (DECISIONS.md D-027).
 */
@Service
public class GuideBlockUseCase {

    private final ProjectAccessGateway accessGateway;
    private final GuideRules rules;
    private final GuideRepository guides;
    private final GuideBlockRepository blocks;
    private final TransactionTemplate transactionTemplate;

    public GuideBlockUseCase(ProjectAccessGateway accessGateway, GuideRules rules, GuideRepository guides,
                             GuideBlockRepository blocks, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.guides = guides;
        this.blocks = blocks;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * One block of a working-copy payload. {@code id} names an existing block of the Guide to keep; an absent
     * or unknown id creates a block. A kept block keeps its type: a different {@code blockType} is an error.
     */
    public record WantedBlock(UUID id, GuideBlockType blockType, GuideTargetType resourceType, UUID resourceId,
                              Map<String, BlockTranslationInput> translations) {
    }

    public BlockView add(UUID projectId, UUID guideId, UnichePrincipal principal, GuideBlockType type,
                         GuideTargetType targetType, UUID targetId, Map<String, BlockTranslationInput> translations) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Guide guide = requireDraftGuide(projectId, guideId);
            GuideBlock block = addIn(projectId, guideId, type, targetType, targetId, translations,
                    rules.blocksOf(projectId, guideId).size());
            rules.reconcileInlineAssets(projectId, guideId);
            touch(guide, principal);
            return rules.blockView(block);
        });
    }

    public BlockView update(UUID projectId, UUID guideId, UUID blockId, UnichePrincipal principal,
                            GuideTargetType targetType, UUID targetId, Map<String, BlockTranslationInput> translations) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Guide guide = requireDraftGuide(projectId, guideId);
            GuideBlock block = rules.requireBlock(projectId, guideId, blockId);
            updateIn(projectId, block, targetType, targetId, translations);
            rules.reconcileInlineAssets(projectId, guideId);
            touch(guide, principal);
            return rules.blockView(block);
        });
    }

    public void remove(UUID projectId, UUID guideId, UUID blockId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        transactionTemplate.executeWithoutResult(status -> {
            Guide guide = requireDraftGuide(projectId, guideId);
            blocks.delete(rules.requireBlock(projectId, guideId, blockId));
            blocks.flush();
            renumber(rules.blocksOf(projectId, guideId));
            rules.reconcileInlineAssets(projectId, guideId);
            touch(guide, principal);
        });
    }

    /** {@code PUT .../blocks:order}: every block id of the Guide, in the new order. */
    public List<BlockView> reorder(UUID projectId, UUID guideId, UnichePrincipal principal, List<UUID> orderedIds) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Guide guide = requireDraftGuide(projectId, guideId);
            List<GuideBlock> current = rules.blocksOf(projectId, guideId);
            Map<UUID, GuideBlock> byId = current.stream().collect(Collectors.toMap(GuideBlock::getId, b -> b));
            List<UUID> ids = orderedIds == null ? List.of() : orderedIds;
            if (ids.size() != current.size() || !new HashSet<>(ids).equals(byId.keySet())) {
                throw new ValidationFailedException("The order must list every block of the guide exactly once.",
                        List.of(new ErrorItem("ids", "INCOMPLETE_ORDER", "List every block of this guide exactly once.")));
            }
            for (int i = 0; i < ids.size(); i++) {
                byId.get(ids.get(i)).moveTo(i);
            }
            blocks.saveAllAndFlush(current);
            touch(guide, principal);
            return rules.view(guide).blocks();
        });
    }

    /**
     * Makes the Guide's blocks exactly {@code wanted}, in that order, inside the caller's transaction and
     * without a status check: the apply of a working copy. Blocks not wanted are removed, kept ones updated,
     * the rest created; then the INLINE media rows follow the text. Errors are reported at {@code blocks[i]}.
     * The Guide itself is touched by the caller's save of its own fields.
     */
    public void replaceBlocks(UUID projectId, UUID guideId, List<WantedBlock> wanted) {
        List<WantedBlock> desired = wanted == null ? List.of() : wanted;
        Map<UUID, GuideBlock> current = rules.blocksOf(projectId, guideId).stream()
                .collect(Collectors.toMap(GuideBlock::getId, b -> b));
        Set<UUID> kept = desired.stream().map(WantedBlock::id).filter(current::containsKey)
                .collect(Collectors.toSet());
        List<GuideBlock> removed = current.values().stream().filter(b -> !kept.contains(b.getId())).toList();
        blocks.deleteAll(removed);
        blocks.flush();

        List<GuideBlock> ordered = new ArrayList<>();
        Set<UUID> used = new HashSet<>();
        for (int i = 0; i < desired.size(); i++) {
            WantedBlock item = desired.get(i);
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

    private GuideBlock addIn(UUID projectId, UUID guideId, GuideBlockType type, GuideTargetType targetType,
                             UUID targetId, Map<String, BlockTranslationInput> translations, int position) {
        List<ErrorItem> errors = new ArrayList<>();
        rules.checkBlock(projectId, type, targetType, targetId, errors);
        Map<String, BlockTranslationInput> normalized = normalize(projectId, type, translations, errors);
        ResourceRules.throwIfAny(errors, "The block is invalid.");
        GuideBlock block = blocks.saveAndFlush(GuideBlock.of(projectId, guideId, type, targetType, targetId, position));
        rules.saveBlockTranslations(block, normalized);
        return block;
    }

    private GuideBlock updateIn(UUID projectId, GuideBlock block, GuideTargetType targetType, UUID targetId,
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
    private void renumber(List<GuideBlock> inOrder) {
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

    private Guide requireDraftGuide(UUID projectId, UUID guideId) {
        Guide guide = rules.requireGuide(projectId, guideId);
        ResourceRules.requireDraft(guide.getStatus(), "guide");
        return guide;
    }

    private void touch(Guide guide, UnichePrincipal principal) {
        guide.touch(principal.subject(), Instant.now());
        guides.saveAndFlush(guide);
    }
}
