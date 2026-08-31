package club.code2create.mcremote.client;

import java.util.List;
import java.util.Objects;

/** One event-ring snapshot returned by {@code events.poll}. */
public record EventPollResult(
        List<MinecraftEvent> events,
        long throughSequence,
        long latestSequence,
        long filteredOut,
        long overflowDroppedTotal,
        long capacityDroppedTotal,
        long explicitlyDiscardedTotal) {

    public EventPollResult {
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        requireNonNegative(throughSequence, "throughSequence");
        requireNonNegative(latestSequence, "latestSequence");
        requireNonNegative(filteredOut, "filteredOut");
        requireNonNegative(overflowDroppedTotal, "overflowDroppedTotal");
        requireNonNegative(capacityDroppedTotal, "capacityDroppedTotal");
        requireNonNegative(explicitlyDiscardedTotal, "explicitlyDiscardedTotal");
        if (throughSequence > latestSequence) {
            throw new IllegalArgumentException("throughSequence must not exceed latestSequence");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }
}
