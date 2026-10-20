package eu.uniche.iguide.asset.application;

import eu.uniche.iguide.platform.web.ApiException;
import eu.uniche.iguide.platform.web.ErrorItem;
import java.util.List;
import org.springframework.http.HttpStatus;

/** An upload above its per-type or the global limit ({@code 08} Milestone 3: the limit is named in the error). */
public class AssetTooLargeException extends ApiException {

    public AssetTooLargeException(String message) {
        super(message, List.of(new ErrorItem("file", "ASSET_TOO_LARGE", message)), null);
    }

    /** "20 MiB", "200 MiB", or the bytes when the limit is not a whole number of MiB. */
    public static String describe(long bytes) {
        long mib = 1024L * 1024L;
        return bytes % mib == 0 ? (bytes / mib) + " MiB" : bytes + " bytes";
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.PAYLOAD_TOO_LARGE;
    }

    @Override
    public String type() {
        return "ASSET_TOO_LARGE";
    }
}
