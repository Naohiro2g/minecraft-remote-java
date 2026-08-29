package club.code2create.mcremote.client;

import java.util.Map;
import java.util.Objects;

/** Structured block input. State may be partial; omitted properties use Minecraft defaults. */
public record BlockSpec(String blockId, Map<String, Object> state) {

    public BlockSpec {
        Objects.requireNonNull(blockId, "blockId");
        if (blockId.isBlank()) {
            throw new IllegalArgumentException("blockId must not be blank");
        }
        state = BlockStates.copyOf(state);
    }

    public static BlockSpec of(String blockId) {
        return new BlockSpec(blockId, Map.of());
    }

    public static BlockSpec of(String blockId, Map<String, ?> state) {
        return new BlockSpec(blockId, BlockStates.copyOf(state));
    }
}
