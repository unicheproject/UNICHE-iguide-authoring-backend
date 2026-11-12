package eu.uniche.iguide.asset.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

/** The container headers {@link MediaDuration} reads, built byte by byte (DECISIONS.md D-043). */
class MediaDurationTest {

    @Test
    void wavIsTheDataChunkOverTheByteRate() {
        assertThat(duration(wav(8_000, 12_000))).isEqualByComparingTo("1.500");
    }

    @Test
    void isoMediaReadsTheMovieHeaderAfterTheMediaData() {
        // ftyp, a large mdat, then moov with mvhd: a recording that was not made streamable.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(box("ftyp", ascii("isom"), new byte[8]));
        out.writeBytes(box("mdat", new byte[5000]));
        ByteBuffer mvhd = ByteBuffer.allocate(100);
        mvhd.put((byte) 0).put(new byte[3]).putInt(0).putInt(0).putInt(600).putInt(600 * 83 + 300);
        out.writeBytes(box("moov", box("mvhd", mvhd.array())));
        assertThat(duration(out.toByteArray())).isEqualByComparingTo("83.500");
    }

    @Test
    void mp3UsesTheXingFrameCountOrElseTheConstantBitrate() {
        // MPEG-1 layer III, 128 kbit/s, 44.1 kHz, stereo: 0xFFFB9000.
        byte[] header = {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x00};
        ByteArrayOutputStream vbr = new ByteArrayOutputStream();
        vbr.writeBytes(id3(100));
        vbr.writeBytes(header);
        vbr.writeBytes(new byte[32]);
        vbr.writeBytes(ascii("Xing"));
        vbr.writeBytes(ByteBuffer.allocate(8).putInt(1).putInt(1000).array());
        vbr.writeBytes(new byte[400]);
        // 1000 frames of 1152 samples at 44100 Hz.
        assertThat(duration(vbr.toByteArray())).isEqualByComparingTo("26.122");

        byte[] cbr = new byte[16_000 * 3];
        System.arraycopy(header, 0, cbr, 0, 4);
        // 48000 bytes at 16000 bytes a second.
        assertThat(duration(cbr)).isEqualByComparingTo("3.000");
    }

    @Test
    void flacIsTheSampleCountOverTheSampleRate() {
        ByteBuffer info = ByteBuffer.allocate(18);
        info.put(new byte[10]);
        long packed = 48_000L << 44 | 1L << 41 | 15L << 36 | 48_000L * 7;
        info.putLong(packed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ascii("fLaC"));
        out.writeBytes(new byte[] {(byte) 0x80, 0, 0, 18});
        out.writeBytes(info.array());
        out.writeBytes(new byte[64]);
        assertThat(duration(out.toByteArray())).isEqualByComparingTo("7.000");
    }

    @Test
    void oggOpusIsTheLastGranuleLessThePreSkipAt48Kilohertz() {
        ByteBuffer head = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN);
        head.put(ascii("OpusHead")).put((byte) 1).put((byte) 2).putShort((short) 312).putInt(48_000).putShort((short) 0)
                .put((byte) 0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(oggPage(0, 7, head.array()));
        out.writeBytes(oggPage(0, 7, new byte[30]));
        out.writeBytes(oggPage(48_000L * 2 + 312, 7, new byte[200]));
        out.writeBytes(oggPage(48_000L * 5 + 312, 7, new byte[200]));
        out.writeBytes(oggPage(48_000L * 99, 8, new byte[10]));
        assertThat(duration(out.toByteArray())).isEqualByComparingTo("5.000");
    }

    @Test
    void webmIsTheInfoDurationInTimecodeScaleUnits() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(ebml(0x1A45DFA3L, 4, ebml(0x4282, 2, ascii("webm"))));
        ByteArrayOutputStream segment = new ByteArrayOutputStream();
        segment.writeBytes(ebml(0x114D9B74L, 4, new byte[12]));
        ByteArrayOutputStream info = new ByteArrayOutputStream();
        info.writeBytes(ebml(0x2AD7B1L, 3, ByteBuffer.allocate(4).putInt(1_000_000).array()));
        info.writeBytes(ebml(0x4489L, 2, ByteBuffer.allocate(8).putDouble(12_345.0).array()));
        segment.writeBytes(ebml(0x1549A966L, 4, info.toByteArray()));
        segment.writeBytes(ebml(0x1F43B675L, 4, new byte[50]));
        out.writeBytes(ebml(0x18538067L, 4, segment.toByteArray()));
        assertThat(duration(out.toByteArray())).isEqualByComparingTo("12.345");
    }

    @Test
    void unknownOrTruncatedFilesHaveNoDuration() {
        assertThat(duration(ascii("%PDF-1.7 not a recording"))).isNull();
        assertThat(duration(Arrays.copyOf(wav(8_000, 12_000), 30))).isNull();
        assertThat(duration(new byte[3])).isNull();
    }

    // --- Builders --------------------------------------------------------------------------------

    private static java.math.BigDecimal duration(byte[] bytes) {
        return MediaDuration.of(new UploadedFile("file", "application/octet-stream", bytes.length,
                new ByteArrayResource(bytes)));
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] wav(int byteRate, int dataBytes) {
        ByteBuffer b = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        b.put(ascii("RIFF")).putInt(36 + dataBytes).put(ascii("WAVE"));
        b.put(ascii("fmt ")).putInt(16).putShort((short) 1).putShort((short) 1).putInt(byteRate).putInt(byteRate)
                .putShort((short) 1).putShort((short) 8);
        b.put(ascii("data")).putInt(dataBytes);
        return b.array();
    }

    private static byte[] box(String type, byte[]... parts) {
        int length = 8 + Arrays.stream(parts).mapToInt(p -> p.length).sum();
        ByteBuffer b = ByteBuffer.allocate(length).putInt(length).put(ascii(type));
        for (byte[] part : parts) {
            b.put(part);
        }
        return b.array();
    }

    private static byte[] id3(int tagBytes) {
        ByteBuffer b = ByteBuffer.allocate(10 + tagBytes);
        b.put(ascii("ID3")).put((byte) 4).put((byte) 0).put((byte) 0)
                .put((byte) (tagBytes >> 21 & 0x7F)).put((byte) (tagBytes >> 14 & 0x7F))
                .put((byte) (tagBytes >> 7 & 0x7F)).put((byte) (tagBytes & 0x7F));
        return b.array();
    }

    private static byte[] oggPage(long granule, int serial, byte[] body) {
        ByteBuffer b = ByteBuffer.allocate(28 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put(ascii("OggS")).put((byte) 0).put((byte) 0).putLong(granule).putInt(serial).putInt(0).putInt(0)
                .put((byte) 1).put((byte) body.length).put(body);
        return b.array();
    }

    /** An EBML element: its id in {@code idBytes} bytes, an 8-byte size, and the data. */
    private static byte[] ebml(long id, int idBytes, byte[] data) {
        ByteBuffer b = ByteBuffer.allocate(idBytes + 8 + data.length);
        for (int i = idBytes - 1; i >= 0; i--) {
            b.put((byte) (id >> (8 * i)));
        }
        b.put((byte) 0x01).put(Arrays.copyOfRange(ByteBuffer.allocate(8).putLong(data.length).array(), 1, 8));
        b.put(data);
        return b.array();
    }
}
