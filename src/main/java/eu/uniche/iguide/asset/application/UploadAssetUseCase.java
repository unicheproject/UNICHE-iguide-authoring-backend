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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stores one uploaded file as a DRAFT Asset ({@code 07} section 10): the type follows the file, the
 * per-type limit is enforced, the checksum and image dimensions are recorded, and the bytes go to object
 * storage before the metadata row is written. If that write then fails, the object is left behind without
 * metadata; nothing references it and a later cleanup can remove it.
 */
@Service
public class UploadAssetUseCase {

    private final ProjectAccessGateway accessGateway;
    private final AssetRules rules;
    private final AssetFileWriter writer;
    private final AssetRepository assets;
    private final TransactionTemplate transactionTemplate;

    public UploadAssetUseCase(ProjectAccessGateway accessGateway, AssetRules rules, AssetFileWriter writer,
                              AssetRepository assets, TransactionTemplate transactionTemplate) {
        this.accessGateway = accessGateway;
        this.rules = rules;
        this.writer = writer;
        this.assets = assets;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * @param requestedType null to take the file's own type; {@code FLOOR_PLAN} to keep an image as a plan
     * @param rawName       the author-side name, the file name when blank
     */
    public AssetView execute(UUID projectId, UnichePrincipal principal, UploadedFile file, AssetType requestedType,
                             String rawName) {
        accessGateway.requireAccess(projectId, principal);
        AssetType detected = file.detectedType();
        AssetType type = detected;
        List<ErrorItem> errors = new ArrayList<>();
        if (requestedType == AssetType.FLOOR_PLAN) {
            if (detected != AssetType.IMAGE) {
                errors.add(new ErrorItem("type", "WRONG_ASSET_TYPE", "Only an image can be a floor plan."));
            }
            type = AssetType.FLOOR_PLAN;
        } else if (requestedType != null && requestedType != detected) {
            errors.add(new ErrorItem("type", "WRONG_ASSET_TYPE", "The file is " + detected.label() + ", not "
                    + requestedType.label() + "."));
        }
        String name = ResourceRules.trimToNull(rawName);
        if (name != null && name.length() > ResourceRules.MAX_NAME) {
            errors.add(new ErrorItem("internalName", "TOO_LONG", "At most " + ResourceRules.MAX_NAME + " characters."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException("The file cannot be uploaded.", errors);
        }
        writer.checkSize(file, type);

        UUID id = UUID.randomUUID();
        Asset.StoredFile stored = writer.write(file, type, AssetFileWriter.originalKey(projectId, id));
        AssetType finalType = type;
        String internalName = name != null ? name : stored.originalFilename();
        return transactionTemplate.execute(status -> rules.view(assets.saveAndFlush(Asset.draft(id, projectId,
                finalType, internalName, stored, principal.subject(), Instant.now()))));
    }
}
