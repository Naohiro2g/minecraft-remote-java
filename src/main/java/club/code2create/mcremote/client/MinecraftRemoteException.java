package club.code2create.mcremote.client;

/** Base checked exception for connection, wire, and remote failures. */
public class MinecraftRemoteException extends Exception {

    public MinecraftRemoteException(String message) {
        super(message);
    }

    public MinecraftRemoteException(String message, Throwable cause) {
        super(message, cause);
    }
}
