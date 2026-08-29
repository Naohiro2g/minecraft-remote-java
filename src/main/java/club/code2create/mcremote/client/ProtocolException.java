package club.code2create.mcremote.client;

/** A malformed or incompatible Minecraft Remote protocol message. */
public final class ProtocolException extends MinecraftRemoteException {

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
