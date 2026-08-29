package club.code2create.mcremote.client;

/** Authentication requires pairing, but no pairing listener was configured. */
public final class PairingRequiredException extends MinecraftRemoteException {

    private final String reason;

    public PairingRequiredException(String reason) {
        super("Minecraft pairing is required (reason=" + reason + ")");
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
