package eu.uniche.iguide.asset.application;

import eu.uniche.iguide.asset.domain.AssetType;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URLConnection;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.core.io.InputStreamSource;

/**
 * One file as received in a multipart upload, with what this backend records about it: a file name without
 * any directory part, the MIME type, the Asset type it implies, its SHA-256 checksum, and image dimensions.
 */
public record UploadedFile(String originalFilename, String declaredContentType, long sizeBytes,
                           InputStreamSource bytes) {

    private static final String OCTET_STREAM = "application/octet-stream";

    /** The name as the author knows it, without any directory part a browser might send. */
    public String filename() {
        String name = originalFilename == null ? "" : originalFilename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        if (name.isEmpty()) {
            return "file";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    /** The declared MIME type, or one guessed from the name when the browser sent none or a generic one. */
    public String mimeType() {
        String type = declaredContentType == null ? "" : declaredContentType.split(";")[0].strip().toLowerCase(Locale.ROOT);
        if (!type.isEmpty() && !type.equals(OCTET_STREAM)) {
            return type;
        }
        String lower = filename().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".glb")) {
            return "model/gltf-binary";
        }
        if (lower.endsWith(".gltf")) {
            return "model/gltf+json";
        }
        String guessed = URLConnection.guessContentTypeFromName(filename());
        return guessed != null ? guessed : OCTET_STREAM;
    }

    /** The Asset type the file implies, from its MIME type and then its extension. */
    public AssetType detectedType() {
        String mime = mimeType();
        if (mime.startsWith("image/")) {
            return AssetType.IMAGE;
        }
        if (mime.startsWith("audio/")) {
            return AssetType.AUDIO;
        }
        if (mime.startsWith("video/")) {
            return AssetType.VIDEO;
        }
        if (mime.startsWith("model/")) {
            return AssetType.THREE_D_MODEL;
        }
        if (mime.equals("application/pdf") || mime.startsWith("text/")
                || mime.startsWith("application/vnd.openxmlformats") || mime.equals("application/msword")) {
            return AssetType.DOCUMENT;
        }
        String lower = filename().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".glb") || lower.endsWith(".gltf") || lower.endsWith(".obj") || lower.endsWith(".usdz")) {
            return AssetType.THREE_D_MODEL;
        }
        return AssetType.OTHER;
    }

    public String sha256() {
        try (DigestInputStream in = new DigestInputStream(open(), MessageDigest.getInstance("SHA-256"))) {
            in.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(in.getMessageDigest().digest());
        } catch (IOException e) {
            throw new UncheckedIOException("The uploaded file could not be read", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Width and height when the JDK can decode the image; formats it cannot read, such as SVG, stay empty. */
    public Integer[] dimensions() {
        try (InputStream in = open()) {
            BufferedImage image = ImageIO.read(in);
            return image == null ? new Integer[2] : new Integer[]{image.getWidth(), image.getHeight()};
        } catch (IOException | RuntimeException e) {
            return new Integer[2];
        }
    }

    public InputStream open() {
        try {
            return bytes.getInputStream();
        } catch (IOException e) {
            throw new UncheckedIOException("The uploaded file could not be read", e);
        }
    }
}
