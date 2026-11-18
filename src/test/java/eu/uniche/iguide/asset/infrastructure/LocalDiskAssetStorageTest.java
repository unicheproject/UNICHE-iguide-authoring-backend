package eu.uniche.iguide.asset.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.uniche.iguide.asset.application.AssetLinkSigner;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDiskAssetStorageTest {

    private static final String KEY = "projects/p/assets/a/original";

    @TempDir
    Path root;

    private LocalDiskAssetStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalDiskAssetStorage(root.toString(), new AssetLinkSigner("0123456789abcdef0123456789abcdef"),
                "https://iguide.example.org/", "/iguide");
    }

    private static ByteArrayInputStream bytes(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void storedBytesAreReadBackAndNoTemporaryFileRemains() throws IOException {
        storage.store(KEY, bytes("hello"), 5, "text/plain");
        assertThat(storage.open(KEY).getContentAsString(StandardCharsets.UTF_8)).isEqualTo("hello");
        try (Stream<Path> files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile)).hasSize(1);
        }
    }

    @Test
    void aSizeMismatchStoresNothing() throws IOException {
        assertThatThrownBy(() -> storage.store(KEY, bytes("hello"), 99, "text/plain"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(storage.open(KEY)).isNull();
        try (Stream<Path> files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void keysThatEscapeTheRootAreRefusedEverywhere() {
        for (String key : new String[]{"../outside", "projects/../../outside", "/etc/passwd", "a\\..\\b", "", "  ",
                "bad\0key", ".", "projects/.."}) {
            assertThatThrownBy(() -> storage.store(key, bytes("x"), 1, "text/plain"))
                    .as("store %s", key).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.open(key)).as("open %s", key).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.delete(key)).as("delete %s", key)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void aSiblingDirectoryWithTheSamePrefixIsNotInsideTheRoot() throws IOException {
        Path sibling = root.resolveSibling(root.getFileName() + "-evil");
        Files.createDirectories(sibling);
        try {
            assertThatThrownBy(() -> storage.open("../" + sibling.getFileName() + "/secret"))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            Files.deleteIfExists(sibling);
        }
    }

    @Test
    void deleteRemovesTheFileAndIsHarmlessWhenItIsAlreadyGone() {
        storage.store(KEY, bytes("hello"), 5, "text/plain");
        storage.delete(KEY);
        assertThat(storage.open(KEY)).isNull();
        storage.delete(KEY);
    }

    @Test
    void aDeleteThatFailsThrowsSoThePurgeJobRetries() throws IOException {
        Path directory = root.resolve("projects/p/assets/a/original");
        Files.createDirectories(directory.resolve("child"));
        // A non-empty directory cannot be deleted as a file: the failure must surface, not be swallowed.
        assertThatThrownBy(() -> storage.delete(KEY)).isInstanceOf(java.io.UncheckedIOException.class);
    }

    @Test
    void signedUrlsNameTheConfiguredOriginAndContextPath() {
        String read = storage.signedReadUrl(KEY, Duration.ofHours(1)).toString();
        assertThat(read).startsWith("https://iguide.example.org/iguide/api/v1/asset-files?key=projects/p/assets/a/original")
                .contains("&disp=inline&sig=");
        assertThat(storage.signedDownloadUrl(KEY, Duration.ofHours(1)).toString()).contains("&disp=attachment&sig=");
    }

    @Test
    void aMissingKeyHasNoSignedUrl() {
        assertThatThrownBy(() -> storage.signedReadUrl("../x", Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMissingDirectorySettingFailsEarly() {
        assertThatThrownBy(() -> new LocalDiskAssetStorage(" ", new AssetLinkSigner("0123456789abcdef0123456789abcdef"),
                "", "")).isInstanceOf(IllegalStateException.class);
    }
}
