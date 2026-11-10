package club.code2create.mcremote.examples;

import club.code2create.mcremote.client.BlockPosition;
import club.code2create.mcremote.client.BlockSpec;
import club.code2create.mcremote.client.BlockValue;
import club.code2create.mcremote.client.CredentialStore;
import club.code2create.mcremote.client.HelloResult;
import club.code2create.mcremote.client.MinecraftRemote;
import club.code2create.mcremote.client.MinecraftRemoteException;
import club.code2create.mcremote.client.ServerTarget;
import java.util.Scanner;

/**
 * Live pulse against a real McRemote: hello, then world.setBlock, then world.getBlock.
 *
 * <p>This is a verification entry point, not a learning sample. It uses the same target,
 * credential, and pairing handling as the examples, and requests the example origin in hello so
 * that no other method runs between hello and the block write. It writes one gold block at the
 * same relative position as example {@code 01}; {@code demo --args=01-cleanup} restores air.
 */
public final class LivePulse {

    private static final String BLOCK = "minecraft:gold_block";

    private LivePulse() {}

    public static void main(String[] args) throws MinecraftRemoteException {
        ExampleArguments options = ExampleArguments.parse(args);
        if (options.credentialMode() == CredentialMode.FORGET) {
            throw new IllegalArgumentException("--forgetはdemoで実行してください");
        }
        ServerTarget target = Main.targetFromEnvironment();
        CredentialStore credentialStore = new CredentialStoreSelector(
                new MicrosoftCredentialStoreProvider(), System.out)
                .select(options.credentialMode(), new Scanner(System.in));
        BlockPosition origin = ExampleWorkspace.requestedOrigin();

        try (MinecraftRemote minecraft = MinecraftRemote.builder(target.host(), target.port())
                .credentialStore(credentialStore)
                .pairingListener(Main::printPairingCommand)
                .buildContext("minecraft:overworld", origin.x(), origin.y(), origin.z())
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
        }
    }
}
