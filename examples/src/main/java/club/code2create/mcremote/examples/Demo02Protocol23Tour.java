package club.code2create.mcremote.examples;

import club.code2create.mcremote.client.BlockSpec;
import club.code2create.mcremote.client.Catalog;
import club.code2create.mcremote.client.EventPollResult;
import club.code2create.mcremote.client.MinecraftEvent;
import club.code2create.mcremote.client.MinecraftRemote;
import club.code2create.mcremote.client.MinecraftRemoteException;
import club.code2create.mcremote.client.PlayerPose;
import club.code2create.mcremote.client.Sign;
import java.util.List;

/** Observes representative protocol 23 capabilities, then writes one visible sign. */
public final class Demo02Protocol23Tour {

    private static final int SIGN_X = 4;
    private static final int SIGN_Z = 4;

    private Demo02Protocol23Tour() {}

    public static void main(String[] args) throws MinecraftRemoteException {
        Main.run("protocol23");
    }

    static void run(MinecraftRemote minecraft) throws MinecraftRemoteException {
        ExampleWorkspace.select(minecraft);
        int signY = AxisFlat.axisY(minecraft);
        minecraft.postToChat("Demo 02: protocol 23 Java capability tour");

        Catalog catalog = minecraft.getCatalog();
        System.out.printf(
                "catalog: blocks=%d, entities=%d, particles=%d%n",
                catalog.blocks().size(), catalog.entityIds().size(), catalog.particleIds().size());
        System.out.println("catalogHash: " + catalog.catalogHash());

        PlayerPose pose = minecraft.getPlayerPose();
        System.out.printf(
                "player pose: %s pos=%s yaw=%.2f pitch=%.2f%n",
                pose.dimension(), pose.position(), pose.yaw(), pose.pitch());

        minecraft.setBlock(SIGN_X, signY, SIGN_Z, BlockSpec.of("minecraft:oak_sign"));
        minecraft.setSign(
                SIGN_X,
                signY,
                SIGN_Z,
                Sign.Content.front(List.of(
                        Sign.LineSpec.styled("Minecraft Remote", "gold", Sign.Decoration.BOLD),
                        Sign.LineSpec.plain("Java / protocol 23"),
                        Sign.LineSpec.plain("catalog + events"),
                        Sign.LineSpec.plain("sign + particle"))));
        minecraft.updateSignLine(
                SIGN_X,
                signY,
                SIGN_Z,
                Sign.Face.FRONT,
                2,
                Sign.LineSpec.styled("events + sign", "aqua"));
        Sign.Value sign = minecraft.getSign(SIGN_X, signY, SIGN_Z);
        System.out.println("sign front: " + sign.front().stream()
                .map(Sign.LineValue::text)
                .toList());

        int accepted = minecraft.spawnParticle(
                SIGN_X,
                signY + 2,
                SIGN_Z,
                0.35,
                0.5,
                0.35,
                "minecraft:flame",
                0.02,
                30);
        System.out.println("particle accepted count: " + accepted);

        EventPollResult events = minecraft.pollEvents(0, 3);
        System.out.printf(
                "events: count=%d through=%d latest=%d filtered=%d%n",
                events.events().size(),
                events.throughSequence(),
                events.latestSequence(),
                events.filteredOut());
        for (MinecraftEvent event : events.events()) {
            System.out.printf(
                    "  #%d %s origin=%s%n",
                    event.sequence(), event.getClass().getSimpleName(), event.origin());
        }

        System.out.printf(
                "相対座標(%d, %d, %d)の看板とflame particleをMinecraftで確認してください。%n",
                SIGN_X, signY, SIGN_Z);
    }
}
