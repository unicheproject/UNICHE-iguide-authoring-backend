package eu.uniche.iguide.asset.application;

import eu.uniche.iguide.asset.application.AssetLinkSigner.Disposition;
import eu.uniche.iguide.asset.domain.Asset;
import eu.uniche.iguide.asset.infrastructure.AssetRepository;
import eu.uniche.iguide.platform.web.ResourceNotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Serves the bytes behind a signed link. The signature is the only credential: it is checked before anything is
 * read, and every failure (bad signature, expiry, unknown key, missing file) answers the same {@code 404}, so a
 * caller cannot tell a forged link from an absent file.
 */
@Service
public class ReadAssetFileQuery {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    /** The only key shapes {@link AssetFileWriter} produces; anything else is refused before the disk is touched. */
    private static final Pattern KEY = Pattern.compile(
            "^projects/(" + UUID_PATTERN + ")/assets/(" + UUID_PATTERN + ")/(?:original|revisions/" + UUID_PATTERN + ")$");

    /** A file to send: its bytes, the stored MIME type, the original name, and how to present it. */
    public record AssetFile(Resource content, String mimeType, String filename, Disposition disposition) {
    }

    private final AssetLinkSigner signer;
    private final AssetStorage storage;
    private final AssetRepository assets;

    public ReadAssetFileQuery(AssetLinkSigner signer, AssetStorage storage, AssetRepository assets) {
        this.signer = signer;
        this.storage = storage;
        this.assets = assets;
    }

    public AssetFile execute(String key, long expiresEpochSecond, String disposition, String signature) {
        Optional<Disposition> parsed = parse(disposition);
        if (parsed.isEmpty() || !signer.verify(key, expiresEpochSecond, parsed.get(), signature, Instant.now())) {
            throw notFound();
        }
        Matcher matcher = KEY.matcher(key);
        if (!matcher.matches()) {
            throw notFound();
        }
        Asset asset = assets.findByProjectIdAndId(UUID.fromString(matcher.group(1)), UUID.fromString(matcher.group(2)))
                .orElseThrow(ReadAssetFileQuery::notFound);
        Resource content = storage.open(key);
        if (content == null) {
            throw notFound();
        }
        return new AssetFile(content, asset.getMimeType(), asset.getOriginalFilename(), parsed.get());
    }

    private static Optional<Disposition> parse(String value) {
        for (Disposition candidate : Disposition.values()) {
            if (candidate.token().equals(value)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("No such file.");
    }
}
