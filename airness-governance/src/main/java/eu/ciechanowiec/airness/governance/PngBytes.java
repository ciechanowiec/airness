package eu.ciechanowiec.airness.governance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.InflaterInputStream;
import lombok.experimental.UtilityClass;

/**
 * Decides from its bytes whether a PNG would render: its signature, a chunk stream that reaches its end
 * marker without running past the file, and image data that inflates.
 *
 * <p>A truncated upload is the defect this reads for. The server answers it with a valid response and the
 * markup that embeds it is correct, so neither a status check nor an accessibility sweep notices that the
 * browser draws a broken image.
 */
@UtilityClass
final class PngBytes {

    private static final int HIGH_BIT = 0x89;
    private static final int END_OF_FILE = 0x1A;
    // The signature as the format specification spells it: a high bit, the name, then a line-ending run
    // that detects a transfer which rewrote line endings.
    private static final byte[] SIGNATURE = {(byte) HIGH_BIT, 'P', 'N', 'G', '\r', '\n', (byte) END_OF_FILE, '\n'};
    private static final int LENGTH_BYTES = 4;
    private static final int TYPE_BYTES = 4;
    private static final int CRC_BYTES = 4;
    private static final int PAYLOAD_OFFSET = LENGTH_BYTES + TYPE_BYTES;
    private static final int OVERHEAD = PAYLOAD_OFFSET + CRC_BYTES;
    private static final String IMAGE_DATA = "IDAT";
    private static final String END = "IEND";

    /**
     * The reason a PNG would not render, if it would not.
     *
     * @param bytes the whole file
     * @return the defect, or nothing when the image is intact
     */
    static Optional<String> problem(byte[] bytes) {
        if (!signed(bytes)) {
            return Optional.of("not a valid PNG (bad signature)");
        }
        List<Chunk> chunks = chunks(bytes);
        return ending(chunks).or(() -> imageProblem(bytes, chunks));
    }

    // A walk stops at the end marker or at the first chunk that runs past the file, so the last chunk
    // read says whether the stream was complete.
    private static Optional<String> ending(List<Chunk> chunks) {
        Optional<Chunk> last = chunks.stream().reduce((_, later) -> later);
        boolean ended = last.filter(chunk -> END.equals(chunk.type())).isPresent();
        return last.filter(Chunk::truncated)
            .map(chunk -> "truncated PNG (chunk \"" + chunk.type() + "\" runs past end of file)")
            .or(() -> ended ? Optional.empty() : Optional.of("truncated PNG (no IEND chunk)"));
    }

    private static Optional<String> imageProblem(byte[] bytes, List<Chunk> chunks) {
        byte[] data = imageData(bytes, chunks);
        return data.length == 0 ? Optional.of("invalid PNG (no IDAT image data)") : inflate(data);
    }

    private static boolean signed(byte[] bytes) {
        return bytes.length >= SIGNATURE.length
            && Arrays.equals(bytes, 0, SIGNATURE.length, SIGNATURE, 0, SIGNATURE.length);
    }

    // Every chunk from the signature up to and including the first that is the end marker or runs past
    // the file. Iterated rather than recursed, because a PNG can carry thousands of chunks.
    private static List<Chunk> chunks(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return Stream.iterate(
            (long) SIGNATURE.length,
            offset -> offset + OVERHEAD <= bytes.length,
            offset -> Chunk.at(buffer, offset).next(bytes.length)
        ).map(offset -> Chunk.at(buffer, offset)).toList();
    }

    private static byte[] imageData(byte[] bytes, List<Chunk> chunks) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        chunks.stream()
            .filter(chunk -> IMAGE_DATA.equals(chunk.type()))
            .forEach(chunk -> data.write(bytes, (int) chunk.payload(), (int) chunk.length()));
        return data.toByteArray();
    }

    // Streamed to nowhere rather than collected, because a large image inflates to many times its size.
    private static Optional<String> inflate(byte[] compressed) {
        try (InputStream inflated = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            inflated.transferTo(OutputStream.nullOutputStream());
            return Optional.empty();
        } catch (IOException _) {
            return Optional.of("corrupt PNG (image data failed to inflate)");
        }
    }

    /**
     * One chunk of the stream, as its header declares it.
     *
     * @param type      the four-character chunk type
     * @param payload   where its payload starts in the file
     * @param length    the payload length it declares
     * @param truncated whether the declared chunk runs past the end of the file
     */
    private record Chunk(String type, long payload, long length, boolean truncated) {

        static Chunk at(ByteBuffer buffer, long offset) {
            int start = (int) offset;
            long length = Integer.toUnsignedLong(buffer.getInt(start));
            byte[] type = new byte[TYPE_BYTES];
            buffer.get(start + LENGTH_BYTES, type);
            long payload = offset + PAYLOAD_OFFSET;
            return new Chunk(
                new String(type, StandardCharsets.ISO_8859_1), payload, length,
                payload + length + CRC_BYTES > buffer.limit()
            );
        }

        // Where the next chunk starts, or the end of the file once the walk has to stop here.
        long next(int fileLength) {
            return this.truncated || END.equals(this.type) ? fileLength : this.payload + this.length + CRC_BYTES;
        }
    }
}
