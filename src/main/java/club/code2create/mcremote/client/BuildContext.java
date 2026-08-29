package club.code2create.mcremote.client;

import java.util.Objects;

/** Server-canonical dimension and origin used by origin-relative build commands. */
public record BuildContext(String dimension, BlockPosition origin) {

    public BuildContext {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(origin, "origin");
        if (dimension.isBlank() || !dimension.contains(":")) {
            throw new IllegalArgumentException("dimension must be fully qualified");
        }
    }
}
