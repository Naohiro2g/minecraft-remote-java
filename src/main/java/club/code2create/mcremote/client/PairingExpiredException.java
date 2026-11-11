package club.code2create.mcremote.client;

import java.time.Duration;

/**
 * The client stopped polling because the pairing code passed its {@code expires_in}.
 *
 * <p>This means the same as the server's {@code pair_expired}: the pairing was not approved in
 * time, or it expired. The client judged it from {@code expires_in}, so the exception carries no
 * JSON-RPC code or server data. An expiry reported by the server arrives as a
 * {@link RemoteCallException} whose {@code reason()} is {@code pair_expired}.
 */
public final class PairingExpiredException extends MinecraftRemoteException {

    /** The reason shared with the server-side {@code pair_expired} error. */
    public static final String REASON = "pair_expired";

    private final Duration expiresIn;

    public PairingExpiredException(Duration expiresIn) {
        super("Minecraft pairing code expired before approval (reason=" + REASON
                + ", judged by the client after " + expiresIn.toSeconds() + "s)");
        this.expiresIn = expiresIn;
    }

    /** Returns {@code pair_expired}. */
    public String reason() {
        return REASON;
    }

    /** Returns the {@code expires_in} the server gave when pairing began. */
    public Duration expiresIn() {
        return expiresIn;
    }
}
