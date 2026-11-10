package club.code2create.mcremote.examples;

import club.code2create.mcremote.client.BlockPosition;
import club.code2create.mcremote.client.BuildContext;
import club.code2create.mcremote.client.MinecraftRemote;
import club.code2create.mcremote.client.MinecraftRemoteException;

/** Chooses an isolated absolute build origin without storing operator-local configuration. */
final class ExampleWorkspace {

    private static final int DEFAULT_ORIGIN_X = 300;
    private static final int DEFAULT_ORIGIN_Y = 0;
    private static final int DEFAULT_ORIGIN_Z = 300;

    private ExampleWorkspace() {}

    static BuildContext select(MinecraftRemote minecraft) throws MinecraftRemoteException {
        BlockPosition origin = requestedOrigin();
        BuildContext context = minecraft.setOrigin(origin.x(), origin.y(), origin.z());
        System.out.println("example workspace origin: " + context.origin());
        return context;
    }

    /** The absolute origin chosen by MCREMOTE_ORIGIN_X/Y/Z, or (300, 0, 300). */
    static BlockPosition requestedOrigin() {
        return new BlockPosition(
                coordinate("MCREMOTE_ORIGIN_X", DEFAULT_ORIGIN_X),
                coordinate("MCREMOTE_ORIGIN_Y", DEFAULT_ORIGIN_Y),
                coordinate("MCREMOTE_ORIGIN_Z", DEFAULT_ORIGIN_Z));
    }

    private static int coordinate(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be an integer", exception);
        }
    }
}
