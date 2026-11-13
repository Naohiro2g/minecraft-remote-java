package club.code2create.mcremote.client;

/**
 * A local TCP failure judged by the client, not reported by the server.
 *
 * <p>{@link CompletionUnknownException} means a request was sent but its completion is unknown;
 * {@link NotSentException} means the request was not sent. Other instances report a connection
 * that could not be opened.
 */
public class TransportException extends MinecraftRemoteException {

    public TransportException(String message) {
        super(message);
    }

    public TransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
