package eu.uniche.iguide.asset.application;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * The playing time of an audio or video file, read from its container's header ({@code 03} section 4
 * {@code durationSeconds}; DECISIONS.md D-043). Nothing is decoded or transcoded ({@code 09} section 2): the
 * formats below record the time, or the frame or sample count it follows from, in their headers.
 *
 * <ul>
 *   <li>ISO base media (MP4, M4A, MOV, 3GP): the {@code mvhd} box;
 *   <li>Matroska and WebM: {@code Duration} and {@code TimecodeScale} of the segment's {@code Info};
 *   <li>MP3: the Xing, Info, or VBRI frame count, else the bitrate of a constant-bitrate file;
 *   <li>WAV: the {@code data} chunk over the byte rate;
 *   <li>Ogg (Vorbis, Opus): the last page's granule position over the sample rate;
 *   <li>FLAC: the {@code STREAMINFO} sample count over the sample rate.
 * </ul>
 *
 * <p>Any other format, or a header that cannot be read, gives {@code null}: the Asset then has no duration, as
 * before.
 */
final class MediaDuration {

    /** The largest value of {@code duration_seconds numeric(10, 3)}. */
    private static final double MAX_SECONDS = 9_999_999.999;
    /** How far past an ID3 tag an MP3 frame header is looked for. */
    private static final int MP3_SEARCH = 64 * 1024;

    private MediaDuration() {
    }

    /** The duration in seconds, rounded to milliseconds, or {@code null} when the file does not tell. */
    static BigDecimal of(UploadedFile file) {
        try (InputStream in = file.open()) {
            return seconds(new Input(new BufferedInputStream(in, MP3_SEARCH + 16)), file.sizeBytes());
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static BigDecimal seconds(Input in, long sizeBytes) throws IOException {
        byte[] head = in.peek(12);
        double seconds;
        if (is(head, 4, "ftyp") || is(head, 4, "moov") || is(head, 4, "mdat") || is(head, 4, "free")
                || is(head, 4, "wide") || is(head, 4, "skip")) {
            seconds = isoMedia(in);
        } else if ((head[0] & 0xFF) == 0x1A && (head[1] & 0xFF) == 0x45 && (head[2] & 0xFF) == 0xDF
                && (head[3] & 0xFF) == 0xA3) {
            seconds = matroska(in);
        } else if (is(head, 0, "RIFF") && is(head, 8, "WAVE")) {
            seconds = wav(in, sizeBytes);
        } else if (is(head, 0, "OggS")) {
            seconds = ogg(in);
        } else if (is(head, 0, "fLaC")) {
            seconds = flac(in);
        } else if (is(head, 0, "ID3") || ((head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0)) {
            seconds = mp3(in, sizeBytes);
        } else {
            return null;
        }
        if (!(seconds > 0) || seconds > MAX_SECONDS) {
            return null;
        }
        return BigDecimal.valueOf(seconds).setScale(3, RoundingMode.HALF_UP);
    }

    // --- ISO base media ------------------------------------------------------------------------

    private static double isoMedia(Input in) throws IOException {
        while (true) {
            long start = in.position();
            long size = in.u32be();
            String type = in.ascii(4);
            if (size == 1) {
                size = in.u64be();
            } else if (size == 0 && !type.equals("moov")) {
                return Double.NaN;
            }
            long headerLength = in.position() - start;
            if (type.equals("moov")) {
                return movieHeader(in, size == 0 ? Long.MAX_VALUE : start + size);
            }
            if (size < headerLength) {
                return Double.NaN;
            }
            in.skip(size - headerLength);
        }
    }

    private static double movieHeader(Input in, long end) throws IOException {
        while (in.position() + 8 <= end) {
            long start = in.position();
            long size = in.u32be();
            String type = in.ascii(4);
            if (size == 1) {
                size = in.u64be();
            }
            long headerLength = in.position() - start;
            if (type.equals("mvhd")) {
                int version = in.u8();
                in.skip(3);
                long timescale;
                long duration;
                if (version == 1) {
                    in.skip(16);
                    timescale = in.u32be();
                    duration = in.u64be();
                    if (duration == -1L) {
                        return Double.NaN;
                    }
                } else {
                    in.skip(8);
                    timescale = in.u32be();
                    duration = in.u32be();
                    if (duration == 0xFFFFFFFFL) {
                        return Double.NaN;
                    }
                }
                return timescale == 0 ? Double.NaN : (double) duration / timescale;
            }
            if (size < headerLength) {
                return Double.NaN;
            }
            in.skip(size - headerLength);
        }
        return Double.NaN;
    }

    // --- Matroska and WebM ---------------------------------------------------------------------

    private static final long EBML_SEGMENT = 0x18538067L;
    private static final long EBML_INFO = 0x1549A966L;
    private static final long EBML_TIMECODE_SCALE = 0x2AD7B1L;
    private static final long EBML_DURATION = 0x4489L;
    private static final long EBML_CLUSTER = 0x1F43B675L;

    private static double matroska(Input in) throws IOException {
        // The EBML header, then the Segment, whose Info comes before the first Cluster.
        long headerId = in.ebmlId();
        long headerSize = in.ebmlSize();
        if (headerId != 0x1A45DFA3L || headerSize < 0) {
            return Double.NaN;
        }
        in.skip(headerSize);
        if (in.ebmlId() != EBML_SEGMENT) {
            return Double.NaN;
        }
        in.ebmlSize();
        while (true) {
            long id = in.ebmlId();
            long size = in.ebmlSize();
            if (id == EBML_INFO && size >= 0) {
                return segmentInfo(in, in.position() + size);
            }
            if (id == EBML_CLUSTER || size < 0) {
                return Double.NaN;
            }
            in.skip(size);
        }
    }

    private static double segmentInfo(Input in, long end) throws IOException {
        long scale = 1_000_000;
        double duration = Double.NaN;
        while (in.position() < end) {
            long id = in.ebmlId();
            long size = in.ebmlSize();
            if (size < 0) {
                return Double.NaN;
            }
            if (id == EBML_TIMECODE_SCALE && size <= 8) {
                scale = in.uintBe((int) size);
            } else if (id == EBML_DURATION && (size == 4 || size == 8)) {
                duration = size == 4 ? Float.intBitsToFloat((int) in.uintBe(4))
                        : Double.longBitsToDouble(in.uintBe(8));
            } else {
                in.skip(size);
            }
        }
        return duration * scale / 1e9;
    }

    // --- WAV -----------------------------------------------------------------------------------

    private static double wav(Input in, long sizeBytes) throws IOException {
        in.skip(12);
        long byteRate = 0;
        while (true) {
            String id = in.ascii(4);
            long size = in.u32le();
            if (id.equals("fmt ")) {
                in.skip(8);
                byteRate = in.u32le();
                in.skip(size - 12 + (size & 1));
            } else if (id.equals("data")) {
                long data = size == 0xFFFFFFFFL || size == 0 ? sizeBytes - in.position() : size;
                return byteRate == 0 ? Double.NaN : (double) data / byteRate;
            } else {
                in.skip(size + (size & 1));
            }
        }
    }

    // --- Ogg -----------------------------------------------------------------------------------

    private static double ogg(Input in) throws IOException {
        long serial = -1;
        long rate = 0;
        long preSkip = 0;
        long granule = -1;
        boolean first = true;
        while (true) {
            String capture;
            try {
                capture = in.ascii(4);
            } catch (EOFException end) {
                break;
            }
            if (!capture.equals("OggS")) {
                return Double.NaN;
            }
            in.skip(2);
            long pageGranule = in.u64le();
            long pageSerial = in.u32le();
            in.skip(8);
            int segments = in.u8();
            int body = 0;
            for (int i = 0; i < segments; i++) {
                body += in.u8();
            }
            if (first) {
                serial = pageSerial;
                byte[] packet = in.read(body);
                if (is(packet, 0, "\u0001vorbis") && packet.length >= 16) {
                    rate = le(packet, 12, 4);
                } else if (is(packet, 0, "OpusHead") && packet.length >= 12) {
                    rate = 48_000;
                    preSkip = le(packet, 10, 2);
                } else {
                    return Double.NaN;
                }
                first = false;
                continue;
            }
            if (pageSerial == serial && pageGranule != -1L) {
                granule = pageGranule;
            }
            in.skip(body);
        }
        return granule < 0 || rate == 0 ? Double.NaN : (double) (granule - preSkip) / rate;
    }

    // --- FLAC ----------------------------------------------------------------------------------

    private static double flac(Input in) throws IOException {
        in.skip(4);
        int header = in.u8();
        long length = in.uintBe(3);
        if ((header & 0x7F) != 0 || length < 18) {
            return Double.NaN;
        }
        byte[] info = in.read(18);
        long packed = be(info, 10, 8);
        long sampleRate = packed >>> 44;
        long samples = packed & 0xFFFFFFFFFL;
        return sampleRate == 0 || samples == 0 ? Double.NaN : (double) samples / sampleRate;
    }

    // --- MP3 -----------------------------------------------------------------------------------

    private static final int[][] BITRATES = {
        {0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448},  // MPEG-1 layer I
        {0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384},     // MPEG-1 layer II
        {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320},      // MPEG-1 layer III
        {0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256},     // MPEG-2 and 2.5 layer I
        {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160},          // MPEG-2 and 2.5 layers II, III
    };
    private static final int[][] SAMPLE_RATES = {
        {11_025, 12_000, 8_000},   // MPEG-2.5
        {0, 0, 0},
        {22_050, 24_000, 16_000},  // MPEG-2
        {44_100, 48_000, 32_000},  // MPEG-1
    };

    private static double mp3(Input in, long sizeBytes) throws IOException {
        byte[] head = in.peek(10);
        if (is(head, 0, "ID3")) {
            long tag = (head[6] & 0x7F) << 21 | (head[7] & 0x7F) << 14 | (head[8] & 0x7F) << 7 | (head[9] & 0x7F);
            in.skip(10 + tag + ((head[5] & 0x10) != 0 ? 10 : 0));
        }
        long audioStart = in.position();
        byte[] window = in.peekUpTo(MP3_SEARCH);
        for (int i = 0; i + 4 <= window.length; i++) {
            if ((window[i] & 0xFF) != 0xFF || (window[i + 1] & 0xE0) != 0xE0) {
                continue;
            }
            int version = (window[i + 1] >> 3) & 0x03;
            int layer = (window[i + 1] >> 1) & 0x03;
            int bitrateIndex = (window[i + 2] >> 4) & 0x0F;
            int rateIndex = (window[i + 2] >> 2) & 0x03;
            if (version == 1 || layer == 0 || bitrateIndex == 0 || bitrateIndex == 15 || rateIndex == 3) {
                continue;
            }
            boolean mpeg1 = version == 3;
            boolean mono = ((window[i + 3] >> 6) & 0x03) == 3;
            int sampleRate = SAMPLE_RATES[version][rateIndex];
            int layerNumber = 4 - layer;
            int samplesPerFrame = layerNumber == 1 ? 384 : layerNumber == 2 || mpeg1 ? 1152 : 576;
            int bitrate = BITRATES[mpeg1 ? layerNumber - 1 : layerNumber == 1 ? 3 : 4][bitrateIndex] * 1000;

            int sideInfo = mpeg1 ? (mono ? 17 : 32) : (mono ? 9 : 17);
            int xing = i + 4 + sideInfo;
            if (layerNumber == 3 && (is(window, xing, "Xing") || is(window, xing, "Info"))
                    && xing + 12 <= window.length && (be(window, xing + 4, 4) & 1) != 0) {
                return (double) be(window, xing + 8, 4) * samplesPerFrame / sampleRate;
            }
            int vbri = i + 4 + 32;
            if (is(window, vbri, "VBRI") && vbri + 18 <= window.length) {
                return (double) be(window, vbri + 14, 4) * samplesPerFrame / sampleRate;
            }
            long audioBytes = sizeBytes - audioStart - i;
            return audioBytes <= 0 ? Double.NaN : audioBytes * 8.0 / bitrate;
        }
        return Double.NaN;
    }

    // --- Bytes ---------------------------------------------------------------------------------

    private static boolean is(byte[] bytes, int offset, String ascii) {
        byte[] wanted = ascii.getBytes(StandardCharsets.ISO_8859_1);
        return offset >= 0 && offset + wanted.length <= bytes.length
                && Arrays.equals(bytes, offset, offset + wanted.length, wanted, 0, wanted.length);
    }

    private static long be(byte[] bytes, int offset, int length) {
        long value = 0;
        for (int i = 0; i < length; i++) {
            value = value << 8 | (bytes[offset + i] & 0xFF);
        }
        return value;
    }

    private static long le(byte[] bytes, int offset, int length) {
        long value = 0;
        for (int i = length - 1; i >= 0; i--) {
            value = value << 8 | (bytes[offset + i] & 0xFF);
        }
        return value;
    }

    /** A forward-only reader that knows its position; {@code peek} looks ahead without consuming. */
    static final class Input {

        private final BufferedInputStream in;
        private long position;

        Input(BufferedInputStream in) {
            this.in = in;
        }

        long position() {
            return position;
        }

        byte[] peek(int length) throws IOException {
            byte[] bytes = peekUpTo(length);
            if (bytes.length < length) {
                return Arrays.copyOf(bytes, length);
            }
            return bytes;
        }

        byte[] peekUpTo(int length) throws IOException {
            in.mark(length);
            byte[] bytes = in.readNBytes(length);
            in.reset();
            return bytes;
        }

        byte[] read(int length) throws IOException {
            byte[] bytes = in.readNBytes(length);
            if (bytes.length < length) {
                throw new EOFException();
            }
            position += length;
            return bytes;
        }

        void skip(long length) throws IOException {
            if (length < 0) {
                throw new IOException("Negative length");
            }
            in.skipNBytes(length);
            position += length;
        }

        int u8() throws IOException {
            int b = in.read();
            if (b < 0) {
                throw new EOFException();
            }
            position++;
            return b;
        }

        String ascii(int length) throws IOException {
            return new String(read(length), StandardCharsets.ISO_8859_1);
        }

        long uintBe(int length) throws IOException {
            return be(read(length), 0, length);
        }

        long u32be() throws IOException {
            return uintBe(4);
        }

        long u64be() throws IOException {
            return uintBe(8);
        }

        long u32le() throws IOException {
            return le(read(4), 0, 4);
        }

        long u64le() throws IOException {
            return le(read(8), 0, 8);
        }

        /** An EBML element id, its length marker kept. */
        long ebmlId() throws IOException {
            int first = u8();
            int length = Integer.numberOfLeadingZeros(first) - 23;
            if (length < 1 || length > 4) {
                throw new IOException("Not an EBML id");
            }
            long id = first;
            for (int i = 1; i < length; i++) {
                id = id << 8 | u8();
            }
            return id;
        }

        /** An EBML element size, or -1 when the size is unknown (all value bits set). */
        long ebmlSize() throws IOException {
            int first = u8();
            int length = Integer.numberOfLeadingZeros(first) - 23;
            if (length < 1 || length > 8) {
                throw new IOException("Not an EBML size");
            }
            long size = first & (0xFF >> length);
            boolean unknown = size == (0xFF >> length);
            for (int i = 1; i < length; i++) {
                int b = u8();
                unknown &= b == 0xFF;
                size = size << 8 | b;
            }
            return unknown ? -1 : size;
        }
    }
}
