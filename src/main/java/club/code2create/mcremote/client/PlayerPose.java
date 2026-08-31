package club.code2create.mcremote.client;

import java.util.Objects;

/** The server-canonical player pose in the connection's origin-relative coordinate system. */
public record PlayerPose(String dimension, Vec3 position, double yaw, double pitch) {

    public PlayerPose {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(position, "position");
        if (!dimension.contains(":")) {
            throw new IllegalArgumentException("dimension must be fully qualified");
        }
        if (!Double.isFinite(yaw)) {
            throw new IllegalArgumentException("yaw must be finite");
        }
        if (!Double.isFinite(pitch)) {
            throw new IllegalArgumentException("pitch must be finite");
        }
    }
}
