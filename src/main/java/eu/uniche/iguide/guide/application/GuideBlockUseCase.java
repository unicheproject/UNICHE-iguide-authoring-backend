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
import eu.uniche.iguide.resource.application.ResourceRules;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The blocks of a DRAFT Guide, a child collection ({@code 07} section 14: {@code .../guides/{id}/blocks},
 * {@code .../blocks/{blockId}}, {@code .../blocks:order}). Positions stay 0-based and contiguous: a new
 * block goes last, removing one closes the gap. A block keeps its type; its target may change.
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

    public BlockView add(UUID projectId, UUID guideId, UnichePrincipal principal, GuideBlockType type,
                         GuideTargetType targetType, UUID targetId, Map<String, BlockTranslationInput> translations) {
        accessGateway.requireAccess(projectId, principal);
        return transactionTemplate.execute(status -> {
            Guide guide = requireDraftGuide(projectId, guideId);
            List<ErrorItem> errors = new ArrayList<>();
            rules.checkBlock(projectId, type, targetType, targetId, errors);
            Map<String, BlockTranslationInput> normalized = normalize(projectId, type, translations, errors);
            ResourceRules.throwIfAny(errors, "The block is invalid.");
            GuideBlock block = blocks.saveAndFlush(GuideBlock.of(projectId, guideId, type, targetType, targetId,
                    rules.blocksOf(projectId, guideId).size()));
            rules.saveBlockTranslations(block, normalized);
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
            List<ErrorItem> errors = new ArrayList<>();
            rules.checkBlock(projectId, block.getBlockType(), targetType, targetId, errors);
            Map<String, BlockTranslationInput> normalized = normalize(projectId, block.getBlockType(), translations, errors);
            ResourceRules.throwIfAny(errors, "The block is invalid.");
            block.target(targetType, targetId);
            blocks.saveAndFlush(block);
            rules.saveBlockTranslations(block, normalized);
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
            List<GuideBlock> remaining = rules.blocksOf(projectId, guideId);
            for (int i = 0; i < remaining.size(); i++) {
                remaining.get(i).moveTo(i);
            }
            blocks.saveAll(remaining);
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
