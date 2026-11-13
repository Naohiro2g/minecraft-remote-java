package club.code2create.mcremote.client;

import java.time.Duration;
import java.util.Objects;

/**
 * TCP keepalive settings used to notice a dead connection while nothing is being sent
 * (2026-11-11-01 (3)). The operating system sends the probes; when they go unanswered, the
 * connection closes and the next call reconnects.
 *
 * <p>The idle time, interval, and probe count apply where the JDK and operating system support
 * them ({@code jdk.net.ExtendedSocketOptions}); elsewhere only keepalive itself is enabled and the
 * operating system's defaults apply.
 *
 * @param idle how long the connection is quiet before the first probe
 * @param interval the time between unanswered probes
 * @param probes how many unanswered probes close the connection
 */
public record KeepAlive(Duration idle, Duration interval, int probes) {

    /**
     * The default: the first probe after 30 seconds of quiet, then every 15 seconds, 8 times.
     * A connection whose peer stopped answering closes after about 2.5 minutes, which rides out a
     * short Wi-Fi drop.
     */
    public static final KeepAlive DEFAULT =
            new KeepAlive(Duration.ofSeconds(30), Duration.ofSeconds(15), 8);

    public KeepAlive {
        requireWholeSeconds(idle, "idle");
        requireWholeSeconds(interval, "interval");
        if (probes < 1) {
            throw new IllegalArgumentException("probes must be positive");
        }
    }

    /** The time a silent peer is tolerated before the connection closes: idle + interval × probes. */
    public Duration tolerance() {
        return idle.plus(interval.multipliedBy(probes));
    }

    private static void requireWholeSeconds(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.toSeconds() < 1 || value.toSeconds() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " must be at least one second");
        }
    }
}
