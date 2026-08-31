package club.code2create.mcremote.client;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A typed projection of the server-authoritative {@code catalog.get} result. */
public record Catalog(
        String catalogHash,
        Map<String, BlockEntry> blocks,
        Set<String> entityIds,
        Set<String> particleIds) {

    public Catalog {
        Objects.requireNonNull(catalogHash, "catalogHash");
        if (catalogHash.isBlank()) {
            throw new IllegalArgumentException("catalogHash must not be blank");
        }
        blocks = immutableMap(blocks, "blocks");
        entityIds = immutableIds(entityIds, "entityIds");
        particleIds = immutableIds(particleIds, "particleIds");
    }

    /** The state schema and canonical default state for one block ID. */
    public record BlockEntry(
            Map<String, List<Object>> states, Map<String, Object> defaultState) {

        public BlockEntry {
            Objects.requireNonNull(states, "states");
            Objects.requireNonNull(defaultState, "defaultState");

            Map<String, List<Object>> copiedStates = new LinkedHashMap<>();
            for (Map.Entry<String, List<Object>> entry : states.entrySet()) {
                String property = nonBlank(entry.getKey(), "state property");
                List<Object> values = List.copyOf(entry.getValue());
                if (values.isEmpty()) {
                    throw new IllegalArgumentException(
                            "catalog state values must not be empty: " + property);
                }
                Class<?> scalarType = null;
                for (Object value : values) {
                    requireScalar(value, "catalog state value");
                    Class<?> currentType = scalarType(value);
                    if (scalarType == null) {
                        scalarType = currentType;
                    } else if (scalarType != currentType) {
                        throw new IllegalArgumentException(
                                "catalog state values must use one JSON scalar type: " + property);
                    }
                }
                if (containsDuplicate(values)) {
                    throw new IllegalArgumentException(
                            "catalog state values must not contain duplicates: " + property);
                }
                copiedStates.put(property, values);
            }

            Map<String, Object> copiedDefault = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : defaultState.entrySet()) {
                String property = nonBlank(entry.getKey(), "default state property");
                Object value = entry.getValue();
                requireScalar(value, "catalog default state value");
                copiedDefault.put(property, value);
            }
            if (!copiedStates.keySet().equals(copiedDefault.keySet())) {
                throw new IllegalArgumentException(
                        "catalog states and defaultState must have the same properties");
            }
            for (Map.Entry<String, Object> entry : copiedDefault.entrySet()) {
                if (copiedStates.get(entry.getKey()).stream()
                        .noneMatch(value -> scalarEquals(value, entry.getValue()))) {
                    throw new IllegalArgumentException(
                            "catalog default state must be one of the declared values: "
                                    + entry.getKey());
                }
            }
            states = Map.copyOf(copiedStates);
            defaultState = Map.copyOf(copiedDefault);
        }
    }

    private static Map<String, BlockEntry> immutableMap(
            Map<String, BlockEntry> values, String name) {
        Objects.requireNonNull(values, name);
        Map<String, BlockEntry> copy = new LinkedHashMap<>();
        values.forEach((id, value) -> copy.put(qualifiedId(id, name), Objects.requireNonNull(value)));
        return Map.copyOf(copy);
    }

    private static Set<String> immutableIds(Set<String> values, String name) {
        Objects.requireNonNull(values, name);
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        values.forEach(id -> copy.add(qualifiedId(id, name)));
        return Set.copyOf(copy);
    }

    private static String qualifiedId(String value, String name) {
        String id = nonBlank(value, name);
        if (!id.contains(":")) {
            throw new IllegalArgumentException(name + " must contain fully qualified IDs");
        }
        return id;
    }

    private static String nonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static void requireScalar(Object value, String name) {
        if (!(value instanceof String || value instanceof Boolean || value instanceof Number)) {
            throw new IllegalArgumentException(name + " must be a string, boolean, or number");
        }
        if (value instanceof Double number && !Double.isFinite(number)) {
            throw new IllegalArgumentException(name + " number must be finite");
        }
        if (value instanceof Float number && !Float.isFinite(number)) {
            throw new IllegalArgumentException(name + " number must be finite");
        }
    }

    private static Class<?> scalarType(Object value) {
        return value instanceof Number ? Number.class : value.getClass();
    }

    private static boolean containsDuplicate(List<Object> values) {
        for (int left = 0; left < values.size(); left++) {
            for (int right = left + 1; right < values.size(); right++) {
                if (scalarEquals(values.get(left), values.get(right))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean scalarEquals(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return new BigDecimal(leftNumber.toString())
                            .compareTo(new BigDecimal(rightNumber.toString()))
                    == 0;
        }
        return left.equals(right);
    }
}
