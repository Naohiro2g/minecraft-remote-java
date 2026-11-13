package club.code2create.mcremote.examples;

import club.code2create.mcremote.client.BlockPosition;
import club.code2create.mcremote.client.BlockSpec;
import club.code2create.mcremote.client.BlockValue;
import club.code2create.mcremote.client.CredentialStore;
import club.code2create.mcremote.client.HelloResult;
import club.code2create.mcremote.client.MinecraftRemote;
import club.code2create.mcremote.client.MinecraftRemoteException;
import club.code2create.mcremote.client.NotSentException;
import club.code2create.mcremote.client.ReconnectEvent;
import club.code2create.mcremote.client.ServerTarget;
import java.util.Scanner;

/**
 * Live pulse against a real McRemote: hello, then world.setBlock, then world.getBlock.
 *
 * <p>This is a verification entry point, not a learning sample. It uses the same target,
 * credential, and pairing handling as the examples, and requests the example origin in hello so
 * that no other method runs between hello and the block write. It writes one gold block at the
 * same relative position as example {@code 01}; {@code demo --args=01-cleanup} restores air.
 *
 * <p>With the argument {@code reconnect}, it then waits until the operator has stopped and
 * restarted the server and pressed Enter, and reads the block again. That call reconnects by
 * itself; the reconnect notice and the result are printed.
 */
public final class LivePulse {

    private static final String BLOCK = "minecraft:gold_block";

    private LivePulse() {}

    public static void main(String[] args) throws MinecraftRemoteException {
        ExampleArguments options = ExampleArguments.parse(args);
        if (options.credentialMode() == CredentialMode.FORGET) {
            throw new IllegalArgumentException("--forgetはdemoで実行してください");
        }
        boolean reconnect = "reconnect".equals(options.selection());
        ServerTarget target = Main.targetFromEnvironment();
        Scanner input = new Scanner(System.in);
        CredentialStore credentialStore = new CredentialStoreSelector(
                new MicrosoftCredentialStoreProvider(), System.out)
                .select(options.credentialMode(), input);
        BlockPosition origin = ExampleWorkspace.requestedOrigin();

        try (MinecraftRemote minecraft = MinecraftRemote.builder(target.host(), target.port())
                .credentialStore(credentialStore)
                .pairingListener(Main::printPairingCommand)
                .buildContext("minecraft:overworld", origin.x(), origin.y(), origin.z())
                .reconnectListener(LivePulse::printReconnect)
                .connect()) {
            HelloResult hello = minecraft.helloResult();
            int y = AxisFlat.axisY(minecraft);
            minecraft.setBlock(2, y, 2, BlockSpec.of(BLOCK));
            BlockValue actual = minecraft.getBlock(2, y, 2);
            boolean pass = BLOCK.equals(actual.blockId());

            System.out.println("pulse hello: protocol=" + hello.protocol()
                    + " mc_version=" + hello.minecraftVersion());
            System.out.println("pulse world.getBlock: " + actual.blockId());
            System.out.println("pulse result: " + (pass ? "PASS" : "FAIL"));
            if (!pass) {
                System.exit(1);
            }
            if (reconnect) {
                System.out.println("pulse reconnect: サーバーを止めて戻したら、Enterを押してください");
                input.nextLine();
                System.out.println("pulse reconnect: connected before the call=" + minecraft.isConnected());
                BlockValue again;
                try {
                    again = minecraft.getBlock(2, y, 2);
                } catch (NotSentException notSent) {
                    // For example PAIRING_REQUIRED when the restart dropped the session token:
                    // reconnecting never pairs by itself (2026-11-11-01 ⑤D).
                    System.out.println("pulse reconnect: not sent, reason=" + notSent.reason());
                    System.exit(1);
                    return;
                }
                boolean same = BLOCK.equals(again.blockId());
                System.out.println("pulse reconnect world.getBlock: " + again.blockId());
                System.out.println("pulse reconnect result: " + (same ? "PASS" : "FAIL"));
                if (!same) {
                    System.exit(1);
                }
            }
        }
    }

    private static void printReconnect(ReconnectEvent event) {
        System.out.println("pulse reconnect event: server_changed=" + event.serverChanged()
                + " session_changed=" + event.sessionChanged()
                + " catalog_hash_changed=" + event.catalogHashChanged()
                + " events_may_have_been_lost=" + event.eventsMayHaveBeenLost()
                + " restored_dimension=" + event.restoredBuild().dimension());
    }
}
