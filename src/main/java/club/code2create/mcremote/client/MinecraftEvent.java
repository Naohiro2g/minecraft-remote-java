package club.code2create.mcremote.client;

import java.util.Objects;
import java.util.Optional;

/** One protocol 23 event returned by {@code events.poll}. */
public sealed interface MinecraftEvent
        permits MinecraftEvent.PickaxePoke,
                MinecraftEvent.ChatPosted,
                MinecraftEvent.ProjectileHit {

    long sequence();

    String dimension();

    BlockPosition origin();

    enum Hand {
        MAIN("main"),
        OFF("off");

        private final String wireName;

        Hand(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        static Hand fromWire(String value) {
            return switch (value) {
                case "main" -> MAIN;
                case "off" -> OFF;
                default -> throw new IllegalArgumentException("unknown hand: " + value);
            };
        }
    }

    record PickaxePoke(
            long sequence,
            String dimension,
            BlockPosition origin,
            BlockPosition position,
            String face,
            BlockValue block,
            Hand hand,
            String item)
            implements MinecraftEvent {

        public PickaxePoke {
            validateCommon(sequence, dimension, origin);
            Objects.requireNonNull(position, "position");
            nonBlank(face, "face");
            Objects.requireNonNull(block, "block");
            Objects.requireNonNull(hand, "hand");
            qualifiedId(item, "item");
        }
    }

    record ChatPosted(
            long sequence,
            String dimension,
            BlockPosition origin,
            String message)
            implements MinecraftEvent {

        public ChatPosted {
            validateCommon(sequence, dimension, origin);
            Objects.requireNonNull(message, "message");
        }
    }

    record ProjectileHit(
            long sequence,
            String dimension,
            BlockPosition origin,
            String projectile,
            Vec3 position,
            ProjectileTarget target)
            implements MinecraftEvent {

        public ProjectileHit {
            validateCommon(sequence, dimension, origin);
            qualifiedId(projectile, "projectile");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(target, "target");
        }
    }

    sealed interface ProjectileTarget
            permits PlayerTarget, EntityTarget, BlockTarget {}

    record PlayerTarget() implements ProjectileTarget {}

    record EntityTarget(String handle) implements ProjectileTarget {

        public EntityTarget {
            nonBlank(handle, "handle");
        }
    }

    record BlockTarget(BlockValue block, BlockPosition position, Optional<String> face)
            implements ProjectileTarget {

        public BlockTarget {
            Objects.requireNonNull(block, "block");
            Objects.requireNonNull(position, "position");
            face = Objects.requireNonNull(face, "face").map(value -> nonBlank(value, "face"));
        }
    }

    private static void validateCommon(
            long sequence, String dimension, BlockPosition origin) {
        if (sequence <= 0) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        qualifiedId(dimension, "dimension");
        Objects.requireNonNull(origin, "origin");
    }

    private static String qualifiedId(String value, String name) {
        String result = nonBlank(value, name);
        if (!result.contains(":")) {
            throw new IllegalArgumentException(name + " must be fully qualified");
        }
        return result;
    }

    private static String nonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
