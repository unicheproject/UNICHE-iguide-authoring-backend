package eu.uniche.iguide.asset.api;

import eu.uniche.iguide.asset.application.AssetLinkSigner.Disposition;
import eu.uniche.iguide.asset.application.ReadAssetFileQuery;
import eu.uniche.iguide.asset.application.ReadAssetFileQuery.AssetFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint for the signed links of {@code LocalDiskAssetStorage} (DECISIONS.md D-047). It takes no bearer
 * token: the HMAC signature in the link is the credential. A {@link Resource} body gets HTTP Range support from
 * Spring, so audio and video stream and seek.
 */
@Tag(name = "Asset files", description = "Bytes of stored Assets, reachable only through a signed, expiring link.")
@RestController
@RequestMapping("/api/v1/asset-files")
public class AssetFileController {

    /** Types a browser may show in place. Everything else is sent as a download, never rendered. */
    private static final Set<String> INLINE_TYPES = Set.of("application/pdf");
    private static final Set<String> INLINE_TYPE_PREFIXES = Set.of("image/", "audio/", "video/", "model/");

    private final ReadAssetFileQuery readQuery;

    public AssetFileController(ReadAssetFileQuery readQuery) {
        this.readQuery = readQuery;
    }

    @Operation(summary = "Read an Asset file through a signed link",
            description = "Links come from the Asset API (the url field and the download endpoint). A wrong, tampered or "
                    + "expired signature answers 404. Supports Range requests (206).")
    @GetMapping
    public ResponseEntity<Resource> file(@RequestParam String key, @RequestParam("exp") long expires,
                                         @RequestParam("disp") String disposition, @RequestParam("sig") String signature) {
        AssetFile file = readQuery.execute(key, expires, disposition, signature);
        MediaType type = parse(file.mimeType());
        boolean attachment = file.disposition() == Disposition.ATTACHMENT || !showsInline(type);
        ContentDisposition header = (attachment ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(file.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(!showsInline(type) ? MediaType.APPLICATION_OCTET_STREAM : type)
                .header(HttpHeaders.CONTENT_DISPOSITION, header.toString())
                // The link is the credential, so only the viewer's own cache may keep the file, and only until it expires.
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                // Uploaded files are untrusted. Same-origin with the CMS, so they must never run as scripts:
                // no sniffing, and a sandbox that gives a directly opened file no origin and no active content.
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                .body(file.content());
    }

    private static MediaType parse(String mimeType) {
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static boolean showsInline(MediaType type) {
        String value = type.getType() + "/" + type.getSubtype();
        return INLINE_TYPES.contains(value) || INLINE_TYPE_PREFIXES.stream().anyMatch(value::startsWith);
    }
}
