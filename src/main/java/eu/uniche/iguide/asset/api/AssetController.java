package eu.uniche.iguide.asset.api;

import eu.uniche.iguide.asset.application.AssetView;
import eu.uniche.iguide.asset.application.ChangeAssetStatusUseCase;
import eu.uniche.iguide.asset.application.GetAssetQuery;
import eu.uniche.iguide.asset.application.ListAssetsQuery;
import eu.uniche.iguide.asset.application.ReplaceAssetFileUseCase;
import eu.uniche.iguide.asset.application.UpdateAssetUseCase;
import eu.uniche.iguide.asset.application.UploadAssetUseCase;
import eu.uniche.iguide.asset.application.UploadedFile;
import eu.uniche.iguide.asset.domain.AssetType;
import eu.uniche.iguide.platform.security.CurrentPrincipal;
import eu.uniche.iguide.platform.web.PageResponse;
import eu.uniche.iguide.resource.api.ChangeStatusRequest;
import eu.uniche.iguide.resource.domain.ResourceReference;
import eu.uniche.iguide.resource.domain.ResourceStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Assets of a Project ({@code 07} sections 10 and 14). Uploads are multipart requests through this backend;
 * {@code file}, {@code usages}, and {@code download} are not in {@code 07} section 14 (DECISIONS.md D-025).
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/assets")
public class AssetController {

    private final ListAssetsQuery listAssets;
    private final GetAssetQuery getAsset;
    private final UploadAssetUseCase uploadAsset;
    private final UpdateAssetUseCase updateAsset;
    private final ChangeAssetStatusUseCase changeStatus;
    private final ReplaceAssetFileUseCase replaceFile;

    public AssetController(ListAssetsQuery listAssets, GetAssetQuery getAsset, UploadAssetUseCase uploadAsset,
                           UpdateAssetUseCase updateAsset, ChangeAssetStatusUseCase changeStatus,
                           ReplaceAssetFileUseCase replaceFile) {
        this.listAssets = listAssets;
        this.getAsset = getAsset;
        this.uploadAsset = uploadAsset;
        this.updateAsset = updateAsset;
        this.changeStatus = changeStatus;
        this.replaceFile = replaceFile;
    }

    @GetMapping
    public PageResponse<AssetResponse> list(@PathVariable UUID projectId,
                                            @RequestParam(required = false) ResourceStatus status,
                                            @RequestParam(required = false) AssetType type,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        return PageResponse.of(listAssets.execute(projectId, CurrentPrincipal.require(), status, type, q, page, size),
                AssetResponse::from);
    }

    /** {@code type} may only ask for {@code FLOOR_PLAN}, for an image, or repeat the file's own type. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AssetResponse> upload(@PathVariable UUID projectId, @RequestPart("file") MultipartFile file,
                                                @RequestParam(required = false) String internalName,
                                                @RequestParam(required = false) AssetType type) {
        AssetView created = uploadAsset.execute(projectId, CurrentPrincipal.require(), uploaded(file), type,
                internalName);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectId + "/assets/"
                + created.asset().getId())).body(AssetResponse.from(created));
    }

    @GetMapping("/{assetId}")
    public AssetResponse get(@PathVariable UUID projectId, @PathVariable UUID assetId) {
        return AssetResponse.from(getAsset.execute(projectId, assetId, CurrentPrincipal.require()));
    }

    @PutMapping("/{assetId}")
    public AssetResponse update(@PathVariable UUID projectId, @PathVariable UUID assetId,
                                @Valid @RequestBody AssetRequests.Update request) {
        return AssetResponse.from(updateAsset.execute(projectId, assetId, CurrentPrincipal.require(),
                request.version(), request.internalName(), request.decorative(), request.translationInputs()));
    }

    @PostMapping("/{assetId}/status")
    public AssetResponse changeStatus(@PathVariable UUID projectId, @PathVariable UUID assetId,
                                      @Valid @RequestBody ChangeStatusRequest request) {
        return AssetResponse.from(changeStatus.execute(projectId, assetId, CurrentPrincipal.require(),
                request.targetStatus(), request.version()));
    }

    @GetMapping("/{assetId}/references")
    public List<ResourceReference> references(@PathVariable UUID projectId, @PathVariable UUID assetId) {
        return getAsset.references(projectId, assetId, CurrentPrincipal.require());
    }

    @GetMapping("/{assetId}/usages")
    public List<AssetResponse.Usage> usages(@PathVariable UUID projectId, @PathVariable UUID assetId) {
        return getAsset.usages(projectId, assetId, CurrentPrincipal.require()).stream()
                .map(AssetResponse.Usage::from).toList();
    }

    /** Replaces the file of a DRAFT Asset with one of the same type; multipart {@code file} and {@code version}. */
    @PostMapping(path = "/{assetId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssetResponse replaceFile(@PathVariable UUID projectId, @PathVariable UUID assetId,
                                     @RequestPart("file") MultipartFile file, @RequestParam Long version) {
        return AssetResponse.from(replaceFile.execute(projectId, assetId, CurrentPrincipal.require(), version,
                uploaded(file)));
    }

    @GetMapping("/{assetId}/download")
    public AssetResponse.Link download(@PathVariable UUID projectId, @PathVariable UUID assetId) {
        GetAssetQuery.SignedLink link = getAsset.download(projectId, assetId, CurrentPrincipal.require());
        return new AssetResponse.Link(link.url(), link.expiresAt());
    }

    private static UploadedFile uploaded(MultipartFile file) {
        return new UploadedFile(file.getOriginalFilename(), file.getContentType(), file.getSize(), file);
    }
}
