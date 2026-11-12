package eu.uniche.iguide.translation.api;

import eu.uniche.iguide.asset.api.AssetResponse;
import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.translation.application.MachineTranslationUseCase;
import eu.uniche.iguide.translation.application.ProvenanceStore;
import eu.uniche.iguide.workingcopy.domain.WorkingCopyResourceType;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Translate with MuseMind" and "mark reviewed" for one language of a Route Stop, a Guide Block, or an Asset
 * ({@code 12} section 3; DECISIONS.md D-036), on the paths of their translation endpoints (D-032). A stop's or block's
 * versions and response are its Route's or Guide's, with {@code X-Working-Copy-Version} after a write into its working
 * copy; an Asset's are its own.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class ChildMachineTranslationController {

    private final MachineTranslationUseCase machineTranslation;

    public ChildMachineTranslationController(MachineTranslationUseCase machineTranslation) {
        this.machineTranslation = machineTranslation;
    }

    /** Body: the Route's {@code version}, and {@code workingCopyVersion} once a copy exists. */
    @PostMapping("/routes/{routeId}/stops/{stopId}/translations/{language}:translate")
    public ResponseEntity<Object> translateStop(@PathVariable UUID projectId, @PathVariable UUID routeId,
                                                @PathVariable UUID stopId, @PathVariable String language,
                                                @RequestBody(required = false) Map<String, Object> body) {
        return translate(projectId, WorkingCopyResourceType.ROUTE, routeId, "stops", stopId, language, body);
    }

    @PostMapping("/routes/{routeId}/stops/{stopId}/translations/{language}:mark-reviewed")
    public ProvenanceStore.Entry markStopReviewed(@PathVariable UUID projectId, @PathVariable UUID routeId,
                                                  @PathVariable UUID stopId, @PathVariable String language) {
        return machineTranslation.markChildReviewed(projectId, WorkingCopyResourceType.ROUTE, routeId, "stops",
                stopId, CurrentPrincipal.require(), language);
    }

    /** Body: the Guide's {@code version}, and {@code workingCopyVersion} once a copy exists. */
    @PostMapping("/guides/{guideId}/blocks/{blockId}/translations/{language}:translate")
    public ResponseEntity<Object> translateBlock(@PathVariable UUID projectId, @PathVariable UUID guideId,
                                                 @PathVariable UUID blockId, @PathVariable String language,
                                                 @RequestBody(required = false) Map<String, Object> body) {
        return translate(projectId, WorkingCopyResourceType.GUIDE, guideId, "blocks", blockId, language, body);
    }

    @PostMapping("/guides/{guideId}/blocks/{blockId}/translations/{language}:mark-reviewed")
    public ProvenanceStore.Entry markBlockReviewed(@PathVariable UUID projectId, @PathVariable UUID guideId,
                                                   @PathVariable UUID blockId, @PathVariable String language) {
        return machineTranslation.markChildReviewed(projectId, WorkingCopyResourceType.GUIDE, guideId, "blocks",
                blockId, CurrentPrincipal.require(), language);
    }

    /** Body: the Asset's {@code version}. */
    @PostMapping("/assets/{assetId}/translations/{language}:translate")
    public AssetResponse translateAsset(@PathVariable UUID projectId, @PathVariable UUID assetId,
                                        @PathVariable String language,
                                        @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> versions = body == null ? Map.of() : body;
        return AssetResponse.from(machineTranslation.translateAsset(projectId, assetId, CurrentPrincipal.require(),
                language, ResourceTranslationController.number(versions.get("version"))));
    }

    @PostMapping("/assets/{assetId}/translations/{language}:mark-reviewed")
    public ProvenanceStore.Entry markAssetReviewed(@PathVariable UUID projectId, @PathVariable UUID assetId,
                                                   @PathVariable String language) {
        return machineTranslation.markAssetReviewed(projectId, assetId, CurrentPrincipal.require(), language);
    }

    private ResponseEntity<Object> translate(UUID projectId, WorkingCopyResourceType type, UUID parentId,
                                             String children, UUID childId, String language,
                                             Map<String, Object> body) {
        Map<String, Object> versions = body == null ? Map.of() : body;
        return ResourceTranslationController.response(machineTranslation.translateChild(projectId, type, parentId,
                children, childId, CurrentPrincipal.require(), language,
                ResourceTranslationController.number(versions.get("version")),
                ResourceTranslationController.number(versions.get("workingCopyVersion"))));
    }
}
