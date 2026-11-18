package eu.uniche.iguide.asset.application;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Signs and checks the links that read a stored file without a bearer token ({@code 07} section 10: a browser
 * cannot send {@code Authorization} from {@code <img>} or {@code <video>}). The signature is an HMAC-SHA256 over
 * {@code key|expires|disposition} with a secret only this application knows, so a link cannot be forged for
 * another file, another disposition, or a later expiry (DECISIONS.md D-047).
 */
@Component
public final class AssetLinkSigner {

    /** How the browser should treat the file. Part of the signed text, so a read link cannot become a download. */
    public enum Disposition {
        INLINE, ATTACHMENT;

        /** The lowercase form used in the signed text and in the {@code disp} query parameter. */
        public String token() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    static final int MIN_SECRET_LENGTH = 32;
    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec secret;

    /** @throws IllegalStateException when the secret is missing or too short to resist guessing. */
    public AssetLinkSigner(@Value("${iguide.assets.signing-key}") String secret) {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("iguide.assets.signing-key must be set to a random secret of at least "
                    + MIN_SECRET_LENGTH + " characters.");
        }
        this.secret = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /** The URL-safe signature for the file {@code key} until {@code expires}. */
    public String sign(String key, Instant expires, Disposition disposition) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac(key, expires.getEpochSecond(), disposition));
    }

    /**
     * True only when {@code signature} is the one {@link #sign} produces for these exact values and {@code now}
     * is before the expiry. The comparison takes the same time whatever the mismatch.
     */
    public boolean verify(String key, long expiresEpochSecond, Disposition disposition, String signature, Instant now) {
        if (key == null || signature == null || disposition == null || now.getEpochSecond() >= expiresEpochSecond) {
            return false;
        }
        byte[] expected = mac(key, expiresEpochSecond, disposition);
        byte[] given;
        try {
            given = Base64.getUrlDecoder().decode(signature);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, given);
    }

    private byte[] mac(String key, long expiresEpochSecond, Disposition disposition) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(secret);
            return mac.doFinal((key + "|" + expiresEpochSecond + "|" + disposition.token())
                    .getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }
}
