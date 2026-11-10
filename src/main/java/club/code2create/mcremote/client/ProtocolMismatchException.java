package club.code2create.mcremote.client;

/**
 * The protocol advertised by the server cannot satisfy the protocol this client requires.
 *
 * <p>The client accepts the same major version with an equal or newer minor version and ignores
 * the patch version. A stored credential is kept, because a version mismatch is not an
 * authentication failure. A mismatch reported by the server arrives as a
 * {@link RemoteCallException} whose {@code reason()} is {@code protocol_mismatch}.
 */
public final class ProtocolMismatchException extends MinecraftRemoteException {

    /** The reason shared with the server-side {@code protocol_mismatch} error. */
    public static final String REASON = "protocol_mismatch";

    private final String serverProtocol;
    private final String clientRequires;

    public ProtocolMismatchException(String serverProtocol, String clientRequires) {
        super("Minecraft Remote protocol mismatch (reason=" + REASON + "): server="
                + serverProtocol + ", client requires=" + clientRequires);
        this.serverProtocol = serverProtocol;
        this.clientRequires = clientRequires;
    }

    /** Returns {@code protocol_mismatch}. */
    public String reason() {
        return REASON;
    }

    /** Returns the protocol advertised by the server. */
    public String serverProtocol() {
        return serverProtocol;
    }

    /** Returns the protocol this client declared in hello. */
    public String clientRequires() {
        return clientRequires;
    }
}
