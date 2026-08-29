package club.code2create.mcremote.examples;

import club.code2create.mcremote.client.BlockSpec;
import club.code2create.mcremote.client.BlockValue;
import club.code2create.mcremote.client.BuildContext;
import club.code2create.mcremote.client.MinecraftRemote;
import club.code2create.mcremote.client.MinecraftRemoteException;

/** Concept example: write one block, then observe its canonical value. */
public final class Demo01SetAndReadBlock {

    private Demo01SetAndReadBlock() {}

    public static void main(String[] args) throws MinecraftRemoteException {
        Main.run("setblock");
    }

    static void run(MinecraftRemote minecraft) throws MinecraftRemoteException {
        BuildContext context = ExampleWorkspace.select(minecraft);
        int x = 2;
        int y = AxisFlat.axisY(minecraft);
        int z = 2;
        String expected = "minecraft:gold_block";

        minecraft.setBlock(x, y, z, BlockSpec.of(expected));
        BlockValue actual = minecraft.getBlock(x, y, z);

        var origin = context.origin();
        System.out.printf("相対座標: (%d, %d, %d)%n", x, y, z);
        System.out.printf(
                "絶対座標: (%d, %d, %d)%n",
                origin.x() + x, origin.y() + y, origin.z() + z);
        System.out.println("期待: " + expected);
        System.out.println("実際: " + actual.blockId());
        System.out.println("判定: " + (expected.equals(actual.blockId()) ? "OK" : "DIFFERENT"));
        System.out.println("片付け: ./gradlew demo --args=01-cleanup");
    }

    static void cleanup(MinecraftRemote minecraft) throws MinecraftRemoteException {
        ExampleWorkspace.select(minecraft);
        int x = 2;
        int y = AxisFlat.axisY(minecraft);
        int z = 2;

        minecraft.setBlock(x, y, z, BlockSpec.of("minecraft:air"));
        System.out.printf("相対座標(%d, %d, %d)をairに戻しました。%n", x, y, z);
    }
}
