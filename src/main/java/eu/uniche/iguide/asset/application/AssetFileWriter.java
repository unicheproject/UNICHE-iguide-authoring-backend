package eu.uniche.iguide.asset.application;

import eu.uniche.iguide.asset.domain.Asset;
import eu.uniche.iguide.asset.domain.AssetType;
import eu.uniche.iguide.platform.web.ErrorItem;
import eu.uniche.iguide.platform.web.ValidationFailedException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Checks an uploaded file against the limits and writes it to object storage ({@code 07} section 10:
 * the backend enforces the size limit, records checksum and MIME type, and writes the object).
 */
@Component
public class AssetFileWriter {

    private final AssetStorage storage;
    private final AssetLimits limits;

    public AssetFileWriter(AssetStorage storage, AssetLimits limits) {
        this.storage = storage;
        this.limits = limits;
    }

    /** Refuses an empty file and one above the limit for {@code type}, naming the limit. */
    public void checkSize(UploadedFile file, AssetType type) {
        if (file.sizeBytes() <= 0) {
            throw new ValidationFailedException("The file is empty.",
                    List.of(new ErrorItem("file", "EMPTY_FILE", "The file is empty.")));
        }
        long limit = limits.limitFor(type);
        if (file.sizeBytes() > limit) {
            throw new AssetTooLargeException("The file is larger than the " + AssetTooLargeException.describe(limit)
                    + " limit for " + type.label() + " files.");
        }
    }

    /** Stores the bytes under {@code key} and returns what was stored. */
    public Asset.StoredFile write(UploadedFile file, AssetType type, String key) {
        String checksum = file.sha256();
        Integer[] dimensions = type.isImage() ? file.dimensions() : new Integer[2];
        try (InputStream data = file.open()) {
            storage.store(key, data, file.sizeBytes(), file.mimeType());
        } catch (IOException e) {
            throw new UncheckedIOException("The uploaded file could not be read", e);
        }
        return new Asset.StoredFile(storage.providerName(), key, file.filename(), file.mimeType(), file.sizeBytes(),
                checksum, dimensions[0], dimensions[1]);
    }

    public static String originalKey(UUID projectId, UUID assetId) {
        return "projects/" + projectId + "/assets/" + assetId + "/original";
    }

    public static String revisionKey(UUID projectId, UUID assetId) {
        return "projects/" + projectId + "/assets/" + assetId + "/revisions/" + UUID.randomUUID();
    }
}
