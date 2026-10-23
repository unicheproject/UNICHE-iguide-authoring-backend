package eu.uniche.iguide.guide.api;

import eu.uniche.iguide.asset.application.ResourceAssetUseCase;
import eu.uniche.iguide.asset.domain.AssetResourceType;
import eu.uniche.iguide.guide.application.GuideBlockUseCase;
import eu.uniche.iguide.guide.application.GuideRules;
import eu.uniche.iguide.guide.application.UpdateGuideCommand;
import eu.uniche.iguide.guide.application.UpdateGuideUseCase;
import eu.uniche.iguide.guide.domain.Guide;
import eu.uniche.iguide.guide.domain.GuideBlockType;
import eu.uniche.iguide.guide.domain.GuideTargetType;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import eu.uniche.iguide.workingcopy.application.LiveResource;
import eu.uniche.iguide.workingcopy.application.LiveRowLock;
import eu.uniche.iguide.workingcopy.application.WorkingCopyHandler;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The working copy of an ACTIVE Guide: the Guide's update body without {@code version}, its blocks in order, and
 * its non-INLINE media (DECISIONS.md D-027). INLINE media follows the text of the blocks on apply.
 */
@Component
public class GuideWorkingCopyHandler implements WorkingCopyHandler<GuideWorkingCopyHandler.State> {

    public record State(String slug, String internalName, Integer estimatedReadingMinutes, boolean featured,
                        Map<String, GuideRequests.GuideTranslationBody> translations, List<BlockState> blocks,
                        List<ResourceAssetUseCase.Media> media) {
    }

    /** A block; {@code id} keeps an existing block, which keeps its type; without one a block is created. */
    public record BlockState(UUID id, GuideBlockType blockType, GuideTargetType resourceType, UUID resourceId,
                             Map<String, GuideRequests.BlockTranslationBody> translations) {
    }

    private final LiveRowLock lock;
    private final GuideRules rules;
    private final UpdateGuideUseCase updateGuide;
    private final GuideBlockUseCase blocks;
    private final ResourceAssetUseCase media;

    public GuideWorkingCopyHandler(LiveRowLock lock, GuideRules rules, UpdateGuideUseCase updateGuide,
                                   GuideBlockUseCase blocks, ResourceAssetUseCase media) {
        this.lock = lock;
        this.rules = rules;
        this.updateGuide = updateGuide;
        this.blocks = blocks;
        this.media = media;
    }

    @Override
    public WorkingCopyResourceType type() {
        return WorkingCopyResourceType.GUIDE;
    }

    @Override
    public Class<State> stateType() {
        return State.class;
    }

    @Override
    public LiveResource lock(UUID projectId, UUID resourceId) {
        Guide guide = lock.lock(Guide.class, projectId, resourceId)
                .orElseThrow(() -> new ResourceNotFoundException("No such guide in this project."));
        return new LiveResource(guide.getStatus(), guide.getVersion());
    }

    @Override
    public void write(UUID projectId, UUID resourceId, UnichePrincipal principal, State state) {
        updateGuide.write(projectId, rules.requireGuide(projectId, resourceId), principal, new UpdateGuideCommand(
                null, state.slug(), state.internalName(), state.estimatedReadingMinutes(), state.featured(),
                GuideRequests.guideInputs(state.translations())));
        List<GuideBlockUseCase.WantedBlock> wanted = state.blocks() == null ? List.of() : state.blocks().stream()
                .map(b -> b == null ? new GuideBlockUseCase.WantedBlock(null, null, null, null, Map.of())
                        : new GuideBlockUseCase.WantedBlock(b.id(), b.blockType(), b.resourceType(), b.resourceId(),
                        GuideRequests.blockInputs(b.translations())))
                .toList();
        blocks.replaceBlocks(projectId, resourceId, wanted);
        media.replace(projectId, AssetResourceType.GUIDE, resourceId, state.media());
    }

    @Override
    public Object render(UUID projectId, UUID resourceId, Instant workingCopyUpdatedAt) {
        return GuideResponse.from(rules.view(rules.requireGuide(projectId, resourceId)), workingCopyUpdatedAt);
    }
}
