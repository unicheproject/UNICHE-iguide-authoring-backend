package eu.uniche.iguide.asset.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import eu.uniche.iguide.asset.application.AssetLinkSigner;
import eu.uniche.iguide.asset.application.AssetLinkSigner.Disposition;
import eu.uniche.iguide.asset.application.PurgeAssetObjectsUseCase;
import eu.uniche.iguide.support.ContentIntegrationTest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

/** The public signed-link endpoint: signature, expiry, Range, presentation, and file removal on replace. */
class AssetFileIntegrationTest extends ContentIntegrationTest {

    @Autowired
    private AssetLinkSigner signer;

    @Autowired
    private PurgeAssetObjectsUseCase purge;

    @BeforeEach
    void setUpLanguages() throws Exception {
        languages(PROJECT_A);
    }

    private static String keyOf(JsonNode asset) {
        return "projects/" + PROJECT_A + "/assets/" + asset.get("id").asText() + "/original";
    }

    private static URI link(String key, long expires, String disposition, String signature) {
        return URI.create("/api/v1/asset-files?key=" + key + "&exp=" + expires + "&disp=" + disposition
                + "&sig=" + signature);
    }

    private static URI signed(String key, Instant expires, Disposition disposition, AssetLinkSigner signer) {
        return link(key, expires.getEpochSecond(), disposition.token(), signer.sign(key, expires, disposition));
    }

    @Test
    void aValidLinkServesTheBytesWithoutABearerTokenAndSendsHardeningHeaders() throws Exception {
        byte[] bytes = png(8, 8);
        JsonNode asset = uploadAsset(PROJECT_A, "kouros.png", "image/png", bytes);
        mockMvc.perform(get(fileLink(asset.get("url").asText())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")))
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("sandbox")));
    }

    @Test
    void aRangeRequestAnswersPartialContent() throws Exception {
        byte[] bytes = new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        JsonNode asset = uploadAsset(PROJECT_A, "clip.mp3", "audio/mpeg", bytes);
        byte[] part = mockMvc.perform(get(fileLink(asset.get("url").asText())).header("Range", "bytes=2-5"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 2-5/10"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(part).containsExactly(2, 3, 4, 5);
    }

    @Test
    void aDownloadLinkIsAnAttachmentUnderTheOriginalName() throws Exception {
        JsonNode asset = uploadAsset(PROJECT_A, "kouros.png", "image/png", png(8, 8));
        JsonNode link = read(mockMvc.perform(authenticated(get(assetUrl(PROJECT_A) + "/{id}/download",
                asset.get("id").asText()))).andReturn());
        mockMvc.perform(get(fileLink(link.get("url").asText())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.startsWith("attachment"),
                                org.hamcrest.Matchers.containsString("kouros.png"))));
    }

    @Test
    void aTamperedSignatureKeyExpiryOrDispositionIsRefused() throws Exception {
        JsonNode asset = uploadAsset(PROJECT_A, "kouros.png", "image/png", png(8, 8));
        String key = keyOf(asset);
        Instant expires = Instant.now().plusSeconds(600);
        String signature = signer.sign(key, expires, Disposition.INLINE);

        mockMvc.perform(get(link(key, expires.getEpochSecond(), "inline", signature))).andExpect(status().isOk());
        mockMvc.perform(get(link(key, expires.getEpochSecond(), "inline", "A" + signature.substring(1))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(link(key, expires.getEpochSecond() + 3600, "inline", signature)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(link(key, expires.getEpochSecond(), "attachment", signature)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(link(key, expires.getEpochSecond(), "bogus", signature))).andExpect(status().isNotFound());
        String otherKey = key.replace("/original", "/revisions/" + UUID.randomUUID());
        mockMvc.perform(get(link(otherKey, expires.getEpochSecond(), "inline", signature)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anExpiredLinkIsRefused() throws Exception {
        JsonNode asset = uploadAsset(PROJECT_A, "kouros.png", "image/png", png(8, 8));
        mockMvc.perform(get(signed(keyOf(asset), Instant.now().minusSeconds(5), Disposition.INLINE, signer)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aSignedLinkForAKeyOutsideTheAssetLayoutIsRefused() throws Exception {
        // Correctly signed, but not a key this application ever writes: it is refused before the disk is read.
        for (String key : new String[]{"../etc/passwd", "projects/x/assets/y/original", "/etc/passwd"}) {
            mockMvc.perform(get(signed(key, Instant.now().plusSeconds(600), Disposition.INLINE, signer)))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void activeContentIsNeverRenderedInline() throws Exception {
        JsonNode asset = uploadAsset(PROJECT_A, "page.html", "text/html", "<script>alert(1)</script>"
                .getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(get(fileLink(asset.get("url").asText())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
    }

    @Test
    void aReplacedFileStaysReadableUntilItsLinksExpireAndThePurgeThenRemovesItFromDisk() throws Exception {
        JsonNode asset = uploadAsset(PROJECT_A, "kouros.png", "image/png", png(8, 8));
        String oldKey = keyOf(asset);
        URI oldLink = fileLink(asset.get("url").asText());
        assertThat(Files.exists(ASSET_DIR.resolve(oldKey))).isTrue();

        JsonNode replaced = read(mockMvc.perform(authenticated(multipart(assetUrl(PROJECT_A) + "/"
                        + asset.get("id").asText() + "/file")
                .file(new MockMultipartFile("file", "new.png", "image/png", png(9, 9)))
                .param("version", asset.get("version").asText()))).andExpect(status().isOk()).andReturn());

        // A page that is open keeps reading the old file until the links already handed out have expired.
        mockMvc.perform(get(oldLink)).andExpect(status().isOk());
        purge.purgeDue(Instant.now());
        assertThat(Files.exists(ASSET_DIR.resolve(oldKey))).isTrue();

        assertThat(purge.purgeDue(Instant.now().plus(java.time.Duration.ofHours(2)))).isPositive();
        assertThat(Files.exists(ASSET_DIR.resolve(oldKey))).isFalse();
        mockMvc.perform(get(oldLink)).andExpect(status().isNotFound());
        mockMvc.perform(get(fileLink(replaced.get("url").asText()))).andExpect(status().isOk());
    }

    @Test
    void aFailedReplaceKeepsTheOldFileAndLeavesNoNewOne() throws Exception {
        JsonNode asset = uploadAsset(PROJECT_A, "kouros.png", "image/png", png(8, 8));
        String keyDir = ASSET_DIR.resolve("projects/" + PROJECT_A + "/assets/" + asset.get("id").asText()).toString();

        mockMvc.perform(authenticated(multipart(assetUrl(PROJECT_A) + "/" + asset.get("id").asText() + "/file")
                .file(new MockMultipartFile("file", "new.png", "image/png", png(9, 9)))
                .param("version", String.valueOf(asset.get("version").asLong() + 99))))
                .andExpect(status().isConflict());

        assertThat(Files.exists(java.nio.file.Path.of(keyDir, "original"))).isTrue();
        assertThat(Files.exists(java.nio.file.Path.of(keyDir, "revisions"))
                ? Files.list(java.nio.file.Path.of(keyDir, "revisions")).count() : 0L).isZero();
    }
}
