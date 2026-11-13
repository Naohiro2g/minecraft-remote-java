package club.code2create.mcremote.client;

/**
 * The request was sent, but the connection closed or was collected before its response arrived,
 * so whether the server carried it out is unknown.
 *
 * <p>The client judged this locally (2026-11-11-01 (2)): the peer closed the connection, the
 * socket failed, the response wait ran out, transport liveness failed, or the application closed
 * the client. It is not a server error and carries no reason. The client does not resend the
 * request; the application decides whether to call again.
 */
public final class CompletionUnknownException extends TransportException {

    public CompletionUnknownException(String message) {
        super(message);
    }

    public CompletionUnknownException(String message, Throwable cause) {
        super(message, cause);
    }
}
