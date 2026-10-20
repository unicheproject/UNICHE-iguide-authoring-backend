package eu.uniche.iguide.asset.application;

import eu.uniche.iguide.asset.domain.Asset;
import eu.uniche.iguide.asset.domain.AssetType;
import eu.uniche.iguide.asset.infrastructure.AssetRepository;
import eu.uniche.iguide.platform.projectaccess.ProjectAccessGateway;
import eu.uniche.iguide.platform.security.UnichePrincipal;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import eu.uniche.iguide.resource.application.ResourceRules;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Replaces the file of a DRAFT Asset, keeping its id, texts, and bindings (DECISIONS.md D-025). The new file
 * is of the same type, so every role that uses the Asset still accepts it. An ACTIVE Asset keeps its file:
 * visitors would see different bytes without any review. The bytes go to a new key, so signed URLs already
 * handed out keep reading the old file until they expire; the old object is left for cleanup.
 */
@Service
public class ReplaceAssetFileUseCase {

    private final ProjectAccessGateway accessGateway;
    private final AssetRules rules;
    private final AssetFileWriter writer;
    private final AssetRepository assets;
    private final TransactionTemplate transactionTemplate;

    public ReplaceAssetFileUseCase(ProjectAccessGateway accessGateway, AssetRules rules, AssetFileWriter writer,
                                   AssetRepository assets, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.writer = writer;
        this.assets = assets;
        this.transactionTemplate = transactionTemplate;
    }

    public AssetView execute(UUID projectId, UUID assetId, UnichePrincipal principal, Long version, UploadedFile file) {
        accessGateway.requireAccess(projectId, principal);
        Asset current = rules.requireAsset(projectId, assetId);
        ResourceRules.requireCurrentVersion(current.getVersion(), version, "asset");
        ResourceRules.requireDraft(current.getStatus(), "asset");
        AssetType detected = file.detectedType();
        boolean sameType = current.getType() == AssetType.FLOOR_PLAN ? detected == AssetType.IMAGE
                : detected == current.getType();
        if (!sameType) {
            throw new ValidationFailedException("The new file is of another type.", List.of(new ErrorItem("file",
                    "WRONG_ASSET_TYPE", "Must be " + (current.getType() == AssetType.FLOOR_PLAN ? "an image"
                    : "a " + current.getType().label() + " file") + ", like the file it replaces.")));
        }
        writer.checkSize(file, current.getType());
        Asset.StoredFile stored = writer.write(file, current.getType(), AssetFileWriter.revisionKey(projectId, assetId));
        return transactionTemplate.execute(status -> {
            Asset asset = rules.requireAsset(projectId, assetId);
            ResourceRules.requireCurrentVersion(asset.getVersion(), version, "asset");
            asset.store(stored, principal.subject(), Instant.now());
            return rules.view(assets.saveAndFlush(asset));
        });
    }
}
