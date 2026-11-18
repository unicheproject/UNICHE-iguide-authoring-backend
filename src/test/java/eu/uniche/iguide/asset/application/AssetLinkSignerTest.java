package eu.uniche.iguide.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.uniche.iguide.asset.application.AssetLinkSigner.Disposition;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AssetLinkSignerTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String KEY = "projects/p/assets/a/original";
    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(3600);

    private final AssetLinkSigner signer = new AssetLinkSigner(SECRET);

    @Test
    void aFreshSignatureVerifies() {
        String signature = signer.sign(KEY, LATER, Disposition.INLINE);
        assertThat(signer.verify(KEY, LATER.getEpochSecond(), Disposition.INLINE, signature, NOW)).isTrue();
    }

    @Test
    void anExpiredLinkIsRefusedEvenWithAValidSignature() {
        String signature = signer.sign(KEY, LATER, Disposition.INLINE);
        assertThat(signer.verify(KEY, LATER.getEpochSecond(), Disposition.INLINE, signature, LATER)).isFalse();
        assertThat(signer.verify(KEY, LATER.getEpochSecond(), Disposition.INLINE, signature, LATER.plusSeconds(1)))
                .isFalse();
    }

    @Test
    void changingAnySignedValueInvalidatesTheSignature() {
        String signature = signer.sign(KEY, LATER, Disposition.INLINE);
        assertThat(signer.verify(KEY + "x", LATER.getEpochSecond(), Disposition.INLINE, signature, NOW)).isFalse();
        assertThat(signer.verify(KEY, LATER.getEpochSecond() + 1, Disposition.INLINE, signature, NOW)).isFalse();
        assertThat(signer.verify(KEY, LATER.getEpochSecond(), Disposition.ATTACHMENT, signature, NOW)).isFalse();
    }

    @Test
    void aTamperedOrMalformedSignatureIsRefused() {
        String signature = signer.sign(KEY, LATER, Disposition.INLINE);
        String flipped = (signature.charAt(0) == 'A' ? "B" : "A") + signature.substring(1);
        assertThat(signer.verify(KEY, LATER.getEpochSecond(), Disposition.INLINE, flipped, NOW)).isFalse();
        assertThat(signer.verify(KEY, LATER.getEpochSecond(), Disposition.INLINE, "", NOW)).isFalse();
        assertThat(signer.verify(KEY, LATER.getEpochSecond(), Disposition.INLINE, "not base64 !!", NOW)).isFalse();
        assertThat(signer.verify(KEY, LATER.getEpochSecond(), Disposition.INLINE, null, NOW)).isFalse();
    }

    @Test
    void aSignatureMadeWithAnotherSecretIsRefused() {
        String forged = new AssetLinkSigner("ffffffffffffffffffffffffffffffff").sign(KEY, LATER, Disposition.INLINE);
        assertThat(signer.verify(KEY, LATER.getEpochSecond(), Disposition.INLINE, forged, NOW)).isFalse();
    }

    @Test
    void aMissingOrShortSecretFailsEarly() {
        assertThatThrownBy(() -> new AssetLinkSigner(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AssetLinkSigner("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AssetLinkSigner("too-short")).isInstanceOf(IllegalStateException.class);
    }
}
