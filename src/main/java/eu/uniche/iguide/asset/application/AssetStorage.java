package eu.uniche.iguide.asset.application;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import org.springframework.core.io.Resource;

/**
 * Port to the store that holds Asset bytes ({@code 07} section 10). Metadata stays in the database; the files
 * are private and are read only through short-lived signed URLs. The only implementation is the local disk
 * (DECISIONS.md D-047).
 */
public interface AssetStorage {

    /** Recorded on the Asset, so a later migration can tell which store holds its bytes. */
    String providerName();

    void store(String key, InputStream data, long sizeBytes, String contentType);

    /** The stored file, readable with Range support, or {@code null} when nothing is stored under {@code key}. */
    Resource open(String key);

    /** Deletes the object; deleting one that does not exist is not an error. */
    void delete(String key);

    /** A URL that reads the object without further credentials until it expires. */
    URI signedReadUrl(String key, Duration validity);

    /** A URL that downloads the object as an attachment; the file name comes from the Asset when it is served. */
    URI signedDownloadUrl(String key, Duration validity);
}
