package eu.uniche.iguide.guide.api;

import eu.uniche.iguide.guide.application.BlockView;
import eu.uniche.iguide.guide.application.ChangeGuideStatusUseCase;
import eu.uniche.iguide.guide.application.CreateGuideUseCase;
import eu.uniche.iguide.guide.application.GetGuideQuery;
import eu.uniche.iguide.guide.application.GuideBlockUseCase;
import eu.uniche.iguide.guide.application.GuideView;
import eu.uniche.iguide.guide.application.ListGuidesQuery;
import eu.uniche.iguide.guide.application.ReorderGuidesUseCase;
import eu.uniche.iguide.guide.application.UpdateGuideCommand;
import eu.uniche.iguide.guide.application.UpdateGuideUseCase;
import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.platform.web.PageResponse;
import eu.uniche.iguide.resource.api.ChangeStatusRequest;
import eu.uniche.iguide.resource.api.OrderRequest;
import eu.uniche.iguide.resource.application.ListParams;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import eu.uniche.iguide.workingcopy.application.WorkingCopyStore;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Guides of a Project and their blocks ({@code 07} section 14, {@code 12} section 7). The class mapping
 * stops at the Project so that {@code guides:order} and {@code blocks:order} sit beside their collections.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class GuideController {

    private final ListGuidesQuery listGuides;
    private final GetGuideQuery getGuide;
    private final CreateGuideUseCase createGuide;
    private final UpdateGuideUseCase updateGuide;
    private final ChangeGuideStatusUseCase changeStatus;
    private final ReorderGuidesUseCase reorderGuides;
    private final GuideBlockUseCase blocks;
    private final WorkingCopyStore workingCopies;

    public GuideController(ListGuidesQuery listGuides, GetGuideQuery getGuide, CreateGuideUseCase createGuide,
                           UpdateGuideUseCase updateGuide, ChangeGuideStatusUseCase changeStatus,
                           ReorderGuidesUseCase reorderGuides, GuideBlockUseCase blocks,
                           WorkingCopyStore workingCopies) {
        this.listGuides = listGuides;
        this.getGuide = getGuide;
        this.createGuide = createGuide;
        this.updateGuide = updateGuide;
        this.changeStatus = changeStatus;
        this.reorderGuides = reorderGuides;
        this.blocks = blocks;
        this.workingCopies = workingCopies;
    }

    @GetMapping("/guides")
    public PageResponse<GuideResponse> list(@PathVariable UUID projectId,
                                            @RequestParam(required = false) ResourceStatus status,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) String language,
                                            @RequestParam(required = false) Boolean featured,
                                            @RequestParam(required = false) String sort,
                                            @RequestParam(required = false) Boolean pending,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        Page<GuideView> result = listGuides.execute(projectId, CurrentPrincipal.require(), new ListParams(status, q,
                language, featured, Boolean.TRUE.equals(pending), sort, page, size));
        Map<UUID, Instant> copies = workingCopies.updatedAt(projectId, WorkingCopyResourceType.GUIDE,
                result.map(view -> view.guide().getId()).getContent());
        return PageResponse.of(result, view -> GuideResponse.from(view, copies.get(view.guide().getId())));
    }

    @PostMapping("/guides")
    public ResponseEntity<GuideResponse> create(@PathVariable UUID projectId,
                                                @RequestBody GuideRequests.Create request) {
        GuideView created = createGuide.execute(projectId, CurrentPrincipal.require(), request.title(), request.slug());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/guides/" + created.guide().getId()))
                .body(GuideResponse.from(created));
    }

    @GetMapping("/guides/{guideId}")
    public GuideResponse get(@PathVariable UUID projectId, @PathVariable UUID guideId) {
        return GuideResponse.from(getGuide.execute(projectId, guideId, CurrentPrincipal.require()),
                workingCopies.updatedAt(projectId, WorkingCopyResourceType.GUIDE, guideId));
    }

    @PutMapping("/guides/{guideId}")
    public GuideResponse update(@PathVariable UUID projectId, @PathVariable UUID guideId,
                                @Valid @RequestBody GuideRequests.Update request) {
        return GuideResponse.from(updateGuide.execute(projectId, guideId, CurrentPrincipal.require(),
                new UpdateGuideCommand(request.version(), request.slug(), request.internalName(),
                        request.estimatedReadingMinutes(), request.featured(), request.translationInputs())));
    }

    @PostMapping("/guides/{guideId}/status")
    public GuideResponse changeStatus(@PathVariable UUID projectId, @PathVariable UUID guideId,
                                      @Valid @RequestBody ChangeStatusRequest request) {
        return GuideResponse.from(changeStatus.execute(projectId, guideId, CurrentPrincipal.require(),
                request.targetStatus(), request.version()));
    }

    @GetMapping("/guides/{guideId}/references")
    public List<ResourceReference> references(@PathVariable UUID projectId, @PathVariable UUID guideId) {
        return getGuide.references(projectId, guideId, CurrentPrincipal.require());
    }

    @PutMapping("/guides:order")
    public ResponseEntity<Void> reorder(@PathVariable UUID projectId, @Valid @RequestBody OrderRequest request) {
        reorderGuides.execute(projectId, CurrentPrincipal.require(), request.ids());
        return ResponseEntity.noContent().build();
    }

    // --- Blocks ----------------------------------------------------------------------------------

    @GetMapping("/guides/{guideId}/blocks")
    public List<GuideResponse.Block> listBlocks(@PathVariable UUID projectId, @PathVariable UUID guideId) {
        return getGuide.blocks(projectId, guideId, CurrentPrincipal.require()).stream()
                .map(GuideResponse.Block::from).toList();
    }

    @PostMapping("/guides/{guideId}/blocks")
    public ResponseEntity<GuideResponse.Block> addBlock(@PathVariable UUID projectId, @PathVariable UUID guideId,
                                                        @RequestBody GuideRequests.Block request) {
        BlockView created = blocks.add(projectId, guideId, CurrentPrincipal.require(), request.blockType(),
                request.resourceType(), request.resourceId(), request.translationInputs());
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/guides/" + guideId + "/blocks/"
                + created.block().getId())).body(GuideResponse.Block.from(created));
    }

    @PutMapping("/guides/{guideId}/blocks/{blockId}")
    public GuideResponse.Block updateBlock(@PathVariable UUID projectId, @PathVariable UUID guideId,
                                           @PathVariable UUID blockId, @RequestBody GuideRequests.Block request) {
        return GuideResponse.Block.from(blocks.update(projectId, guideId, blockId, CurrentPrincipal.require(),
                request.resourceType(), request.resourceId(), request.translationInputs()));
    }

    @DeleteMapping("/guides/{guideId}/blocks/{blockId}")
    public ResponseEntity<Void> removeBlock(@PathVariable UUID projectId, @PathVariable UUID guideId,
                                            @PathVariable UUID blockId) {
        blocks.remove(projectId, guideId, blockId, CurrentPrincipal.require());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/guides/{guideId}/blocks:order")
    public List<GuideResponse.Block> reorderBlocks(@PathVariable UUID projectId, @PathVariable UUID guideId,
                                                   @Valid @RequestBody OrderRequest request) {
        return blocks.reorder(projectId, guideId, CurrentPrincipal.require(), request.ids()).stream()
                .map(GuideResponse.Block::from).toList();
    }
}
