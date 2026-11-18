package eu.uniche.iguide.asset.infrastructure;

import eu.uniche.iguide.asset.application.AssetLinkSigner;
import eu.uniche.iguide.asset.application.AssetLinkSigner.Disposition;
import eu.uniche.iguide.asset.application.AssetStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The only {@link AssetStorage}: files on local disk under one root, a Docker volume in deployment
 * (DECISIONS.md D-047). Like MuseotekBox's local media storage, it works while the backend runs as one
 * instance. Files are written to a temporary name and moved into place, so a reader never sees half a file.
 * Signed URLs name the endpoint of {@code AssetFileController}; {@code iguide.assets.public-base-url} adds the
 * origin when the browser does not reach the API under a relative path.
 */
@Component
public class LocalDiskAssetStorage implements AssetStorage {

    /** Path of the public file endpoint below the context path. */
    public static final String FILE_PATH = "/api/v1/asset-files";

    private static final Logger log = LoggerFactory.getLogger(LocalDiskAssetStorage.class);

    private final Path root;
    private final AssetLinkSigner signer;
    private final String linkBase;

    public LocalDiskAssetStorage(@Value("${iguide.assets.dir}") String dir,
                                 AssetLinkSigner signer,
                                 @Value("${iguide.assets.public-base-url:}") String publicBaseUrl,
                                 @Value("${server.servlet.context-path:}") String contextPath) {
        if (dir == null || dir.isBlank()) {
            throw new IllegalStateException("iguide.assets.dir is not set; asset storage needs a directory.");
        }
        this.signer = signer;
        this.root = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the asset directory: " + root, e);
        }
        // Fail at startup, not on the first upload: a volume the application cannot write to surfaces here.
        if (!Files.isDirectory(root) || !Files.isWritable(root)) {
            throw new IllegalStateException("The asset directory is not a writable directory: " + root);
        }
        String origin = publicBaseUrl == null ? "" : publicBaseUrl.strip().replaceAll("/+$", "");
        this.linkBase = origin + (contextPath == null ? "" : contextPath) + FILE_PATH;
    }

    @Override
    public String providerName() {
        return "LOCAL";
    }

    @Override
    public void store(String key, InputStream data, long sizeBytes, String contentType) {
        Path target = safeResolve(key);
        Path temp = null;
        try {
            Files.createDirectories(target.getParent());
            temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            long written = Files.copy(data, temp, StandardCopyOption.REPLACE_EXISTING);
            if (written != sizeBytes) {
                throw new IllegalStateException("Expected " + sizeBytes + " bytes for " + key + " but read " + written);
            }
            move(temp, target);
            temp = null;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store asset file " + key, e);
        } finally {
            deleteQuietly(temp);
        }
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public Resource open(String key) {
        Path file = safeResolve(key);
        return Files.isRegularFile(file) && Files.isReadable(file) ? new FileSystemResource(file) : null;
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(safeResolve(key));
        } catch (IOException e) {
            // Thrown, not swallowed: the purge job keeps its queue entry and retries on the next run.
            throw new UncheckedIOException("Failed to delete asset file " + key, e);
        }
    }

    @Override
    public URI signedReadUrl(String key, Duration validity) {
        return link(key, validity, Disposition.INLINE);
    }

    @Override
    public URI signedDownloadUrl(String key, Duration validity) {
        return link(key, validity, Disposition.ATTACHMENT);
    }

    private URI link(String key, Duration validity, Disposition disposition) {
        safeResolve(key);
        Instant expires = Instant.now().plus(validity);
        return UriComponentsBuilder.fromUriString(linkBase)
                .queryParam("key", key)
                .queryParam("exp", expires.getEpochSecond())
                .queryParam("disp", disposition.token())
                .queryParam("sig", signer.sign(key, expires, disposition))
                .build().encode().toUri();
    }

    /** Path traversal guard: whatever the key, the resolved path stays strictly inside the root. */
    private Path safeResolve(String key) {
        if (key == null || key.isBlank() || key.indexOf('\0') >= 0 || key.startsWith("/") || key.contains("\\")) {
            throw new IllegalArgumentException("Not a valid asset key");
        }
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IllegalArgumentException("The asset key escapes the asset directory");
        }
        return target;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not remove temporary file {}", path, e);
        }
    }
}
