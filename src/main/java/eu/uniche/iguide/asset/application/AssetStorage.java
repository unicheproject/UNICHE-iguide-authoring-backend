package eu.uniche.iguide.asset.application;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;

/**
 * Port to the object store that holds Asset bytes ({@code 07} section 10). Metadata stays in the database;
 * the store is private and is read only through short-lived signed URLs. The only implementation is
 * S3-compatible ({@code 10} section 7).
 */
public interface AssetStorage {

    /** Recorded on the Asset, so a later migration can tell which store holds its bytes. */
    String providerName();

    void store(String key, InputStream data, long sizeBytes, String contentType);

    /** A URL that reads the object without further credentials until it expires. */
    URI signedReadUrl(String key, Duration validity);

    /** A URL that downloads the object as an attachment named {@code filename}. */
    URI signedDownloadUrl(String key, Duration validity, String filename);
}
