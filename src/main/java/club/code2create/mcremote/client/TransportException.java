package club.code2create.mcremote.client;

/** A local TCP or framing failure whose remote completion may be unknown. */
public final class TransportException extends MinecraftRemoteException {

    public TransportException(String message) {
        super(message);
    }

    public TransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
