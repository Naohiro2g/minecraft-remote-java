package club.code2create.mcremote.client;

import java.util.Objects;

/**
 * What changed when the client reconnected (2026-11-11-01 (5)).
 *
 * <p>Events that happened while disconnected may be lost, because each connection has its own event
 * ring; an event cursor from the previous connection belongs to that connection. Requests from the
 * previous connection were not resent.
 *
 * @param previous the hello result of the connection that closed
 * @param current the hello result of the new connection
 * @param restoredBuild the dimension and origin the new hello asked for: the last ones the server
 *     confirmed on the previous connection
 */
public record ReconnectEvent(HelloResult previous, HelloResult current, BuildContext restoredBuild) {

    public ReconnectEvent {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(restoredBuild, "restoredBuild");
    }

    /** Always true: events are kept per connection, so those in between may be lost. */
    public boolean eventsMayHaveBeenLost() {
        return true;
    }

    /** Whether hello reported a different {@code server}. */
    public boolean serverChanged() {
        return !previous.server().equals(current.server());
    }

    /** Whether hello reported a different {@code session}. */
    public boolean sessionChanged() {
        return !previous.session().equals(current.session());
    }

    /** Whether hello reported a different {@code catalogHash}; a cached catalog is then stale. */
    public boolean catalogHashChanged() {
        return !previous.catalogHash().equals(current.catalogHash());
    }
}
