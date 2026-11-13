package club.code2create.mcremote.client;

import java.util.Objects;

/**
 * The request was not sent, because no usable connection could be made for it
 * (2026-11-11-01 (5) A). Unlike {@link CompletionUnknownException}, the server did not receive
 * the request.
 */
public final class NotSentException extends TransportException {

    /** Why no connection could carry the request. */
    public enum Reason {
        /** Reconnecting did not open a connection before the reconnect window ran out. */
        CONNECTION_FAILED,
        /** The new connection's hello advertised an incompatible protocol; it was closed. */
        INCOMPATIBLE_PROTOCOL,
        /** The server answered {@code auth_required}; reconnecting never pairs by itself. */
        PAIRING_REQUIRED,
        /** The new connection's hello failed with another server error. */
        HELLO_FAILED,
        /** The connection is closed and automatic reconnecting is disabled. */
        DISCONNECTED
    }

    private final Reason reason;

    public NotSentException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /** Returns why the request was not sent. */
    public Reason reason() {
        return reason;
    }
}
