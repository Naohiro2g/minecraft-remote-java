package club.code2create.mcremote.examples;

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
        int x = coordinate("MCREMOTE_ORIGIN_X", DEFAULT_ORIGIN_X);
        int y = coordinate("MCREMOTE_ORIGIN_Y", DEFAULT_ORIGIN_Y);
        int z = coordinate("MCREMOTE_ORIGIN_Z", DEFAULT_ORIGIN_Z);
        BuildContext context = minecraft.setOrigin(x, y, z);
        System.out.println("example workspace origin: " + context.origin());
        return context;
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
