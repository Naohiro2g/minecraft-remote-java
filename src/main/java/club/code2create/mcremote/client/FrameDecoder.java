package club.code2create.mcremote.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits direct-TCP bytes into LF-terminated UTF-8 JSON frames.
 *
 * <p>Bytes are buffered until LF, so a chunk may end inside a UTF-8 character or a JSON value.
 * Lines that are not valid UTF-8 JSON are discarded. An unterminated tail is never returned as a
 * frame, including at EOF.
 */
final class FrameDecoder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ByteArrayOutputStream tail = new ByteArrayOutputStream();

    /** Feeds one received chunk and returns the complete JSON values it finished. */
    List<JsonNode> feed(byte[] chunk, int offset, int length) {
        List<JsonNode> frames = new ArrayList<>();
        int lineStart = offset;
        int end = offset + length;
        for (int index = offset; index < end; index++) {
            if (chunk[index] != '\n') {
                continue;
            }
            tail.write(chunk, lineStart, index - lineStart);
            byte[] line = tail.toByteArray();
            tail.reset();
            lineStart = index + 1;
            JsonNode frame = parse(line);
            if (frame != null) {
                frames.add(frame);
            }
        }
        tail.write(chunk, lineStart, end - lineStart);
        return frames;
    }

    List<JsonNode> feed(byte[] chunk) {
        return feed(chunk, 0, chunk.length);
    }

    /** Returns the bytes received after the last LF. */
    byte[] tail() {
        return tail.toByteArray();
    }

    private static JsonNode parse(byte[] line) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(line))
                    .toString();
        } catch (CharacterCodingException ignored) {
            // The protocol discards lines that are not JSON; invalid UTF-8 cannot be JSON text.
            return null;
        }
        try {
            JsonNode value = MAPPER.readTree(text);
            return value == null || value.isMissingNode() ? null : value;
        } catch (JsonProcessingException ignored) {
            // The protocol explicitly discards non-JSON lines.
            return null;
        }
    }
}
