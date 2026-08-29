package club.code2create.mcremote.client;

import java.util.Map;
import java.util.Objects;

/** Immutable canonical block snapshot returned by Minecraft Remote. */
public record BlockValue(String blockId, Map<String, Object> state) {

    public BlockValue {
        Objects.requireNonNull(blockId, "blockId");
        if (blockId.isBlank() || !blockId.contains(":")) {
            throw new IllegalArgumentException("BlockValue blockId must be fully qualified");
        }
        state = BlockStates.copyOf(state);
    }
}
