package club.code2create.mcremote.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BlockSpecTest {

    @Test
    void copiesAndSortsStateForDeterministicEncoding() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("waterlogged", false);
        source.put("axis", "z");

        BlockSpec block = BlockSpec.of("minecraft:oak_log", source);
        source.put("axis", "x");

        assertEquals(List.of("axis", "waterlogged"), List.copyOf(block.state().keySet()));
        assertEquals("z", block.state().get("axis"));
        assertThrows(UnsupportedOperationException.class, () -> block.state().put("axis", "y"));
    }

    @Test
    void rejectsValuesOutsideTheProtocolScalarSet() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BlockSpec.of("minecraft:stone", Map.of("bad", List.of("x"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> BlockSpec.of("minecraft:stone", Map.of("bad", Double.NaN)));
    }
}
