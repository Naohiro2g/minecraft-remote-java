package club.code2create.mcremote.client;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

final class BlockStates {

    private BlockStates() {}

    static Map<String, Object> copyOf(Map<String, ?> source) {
        Objects.requireNonNull(source, "state");
        TreeMap<String, Object> copy = new TreeMap<>();
        source.forEach(
                (name, value) -> {
                    if (name == null || name.isBlank()) {
                        throw new IllegalArgumentException("state property names must not be blank");
                    }
                    if (!isScalar(value)) {
                        throw new IllegalArgumentException(
                                "state." + name + " must be a JSON boolean, number, or string");
                    }
                    copy.put(name, value);
                });
        return Collections.unmodifiableMap(copy);
    }

    private static boolean isScalar(Object value) {
        if (value instanceof Double number) {
            return Double.isFinite(number);
        }
        if (value instanceof Float number) {
            return Float.isFinite(number);
        }
        return value instanceof Boolean || value instanceof Number || value instanceof String;
    }
}
