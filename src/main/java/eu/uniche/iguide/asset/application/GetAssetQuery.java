package eu.uniche.iguide.asset.application;

import eu.uniche.iguide.asset.domain.Asset;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.resource.domain.ResourceReference;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Reads one Asset, where it is used, and a download link for its file. */
@Service
public class GetAssetQuery {

    /** A signed URL and when it stops working. */
    public record SignedLink(URI url, Instant expiresAt) {
    }

    private final ProjectAccessGateway accessGateway;
    private final AssetRules rules;
    private final AssetStorage storage;

    public GetAssetQuery(ProjectAccessGateway accessGateway, AssetRules rules, AssetStorage storage) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.storage = storage;
    }

    public AssetView execute(UUID projectId, UUID assetId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        return rules.view(rules.requireAsset(projectId, assetId));
    }

    /** {@code GET .../references}: the non-archived Resources and Floor Plans that keep the Asset in use. */
    public List<ResourceReference> references(UUID projectId, UUID assetId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        rules.requireAsset(projectId, assetId);
        return rules.referencesTo(projectId, assetId);
    }

    /** Every Resource and Floor Plan that uses the Asset, with its roles there (DECISIONS.md D-025). */
    public List<AssetRules.ResourceUsage> usages(UUID projectId, UUID assetId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        rules.requireAsset(projectId, assetId);
        return rules.usagesOf(projectId, assetId);
    }

    /** A short-lived link that downloads the file under its original name (DECISIONS.md D-025). */
    public SignedLink download(UUID projectId, UUID assetId, UnichePrincipal principal) {
        accessGateway.requireAccess(projectId, principal);
        Asset asset = rules.requireAsset(projectId, assetId);
        return new SignedLink(storage.signedDownloadUrl(asset.getStorageKey(), AssetRules.URL_VALIDITY,
                asset.getOriginalFilename()), Instant.now().plus(AssetRules.URL_VALIDITY));
    }
}
