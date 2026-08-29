package club.code2create.mcremote.examples;

import club.code2create.mcremote.client.BlockSpec;
import club.code2create.mcremote.client.BuildContext;
import club.code2create.mcremote.client.MinecraftRemote;
import club.code2create.mcremote.client.MinecraftRemoteException;

/** Prepares a visible coordinate workspace inside the build-permission boundary. */
public final class AxisFlat {

    private static final int FLAT_WIDTH = 48;
    private static final int AXIS_WIDTH = 40;
    private static final int AXIS_TOP = 127;
    private static final int AXIS_Y_V_ORG = 96;
    private static final int AXIS_BOTTOM = 63;
    private static final int FLOOR_Y = AXIS_BOTTOM - 1;
    private static final int MAX_TILE_EDGE = 64;
    private static final int DRAW_DELAY_MILLIS = 50;

    private AxisFlat() {}

    public static void main(String[] args) throws MinecraftRemoteException {
        Main.run(args.length > 0 && "clear".equals(args[0]) ? "axis-flat-clear" : "axis-flat");
    }

    static void prepare(MinecraftRemote minecraft) throws MinecraftRemoteException {
        BuildContext context = ExampleWorkspace.select(minecraft);
        int floorY = floorY(minecraft);
        int axisY = axisY(minecraft);
        int topY = relativeY(minecraft, AXIS_TOP);
        int bottomY = relativeY(minecraft, AXIS_BOTTOM);
        minecraft.postToChat("AxisFlat: clearing a workspace and drawing XYZ axes");

        clearFromTopDown(minecraft, bottomY, topY);
        fillLayer(minecraft, floorY, BlockSpec.of("minecraft:grass_block"));

        drawXAxis(minecraft, axisY);
        drawYAxis(minecraft);
        drawZAxis(minecraft, axisY);
        minecraft.setBlock(0, axisY, 0, BlockSpec.of("minecraft:emerald_block"));

        printCoordinates(context, floorY, axisY, topY);
        System.out.println("X軸: diamond_block / Y軸: sea_lantern / Z軸: gold_block");
        System.out.println("原点: emerald_block");
        System.out.println("注意: この操作はworldを変更し、平面は自動復元しません。");
        System.out.println("軸だけ消す: ./gradlew demo --args=axis-flat-clear");
    }

    static void clearAxes(MinecraftRemote minecraft) throws MinecraftRemoteException {
        ExampleWorkspace.select(minecraft);
        int axisY = axisY(minecraft);
        int bottomY = relativeY(minecraft, AXIS_BOTTOM);
        int topY = relativeY(minecraft, AXIS_TOP);
        minecraft.setBlocks(
                -AXIS_WIDTH,
                axisY,
                0,
                AXIS_WIDTH,
                axisY,
                0,
                BlockSpec.of("minecraft:air"));
        minecraft.setBlocks(
                0,
                axisY,
                -AXIS_WIDTH,
                0,
                axisY,
                AXIS_WIDTH,
                BlockSpec.of("minecraft:air"));
        minecraft.setBlocks(
                0, bottomY, 0, 0, topY, 0, BlockSpec.of("minecraft:air"));
        minecraft.postToChat("AxisFlat: XYZ axes cleared; the flat workspace remains");

        System.out.println("XYZ軸を消しました。整地した平面は残しています。");
    }

    static int axisY(MinecraftRemote minecraft) {
        return relativeY(minecraft, AXIS_Y_V_ORG);
    }

    private static int floorY(MinecraftRemote minecraft) {
        return relativeY(minecraft, FLOOR_Y);
    }

    private static int relativeY(MinecraftRemote minecraft, int absoluteY) {
        return absoluteY - minecraft.buildContext().origin().y();
    }

    private static void clearFromTopDown(MinecraftRemote minecraft, int bottomY, int topY)
            throws MinecraftRemoteException {
        BlockSpec air = BlockSpec.of("minecraft:air");
        for (int y = topY; y >= bottomY; y--) {
            fillLayer(minecraft, y, air);
            if ((topY - y + 1) % 8 == 0 || y == bottomY) {
                System.out.printf("整地中: 相対y=%dまでclear%n", y);
            }
        }
    }

    private static void fillLayer(MinecraftRemote minecraft, int y, BlockSpec block)
            throws MinecraftRemoteException {
        for (int x1 = -FLAT_WIDTH; x1 <= FLAT_WIDTH; x1 += MAX_TILE_EDGE) {
            int x2 = Math.min(x1 + MAX_TILE_EDGE - 1, FLAT_WIDTH);
            for (int z1 = -FLAT_WIDTH; z1 <= FLAT_WIDTH; z1 += MAX_TILE_EDGE) {
                int z2 = Math.min(z1 + MAX_TILE_EDGE - 1, FLAT_WIDTH);
                minecraft.setBlocks(x1, y, z1, x2, y, z2, block);
                waitForNextServerTick();
            }
        }
    }

    private static void drawXAxis(MinecraftRemote minecraft, int y)
            throws MinecraftRemoteException {
        BlockSpec block = BlockSpec.of("minecraft:diamond_block");
        for (int x = -AXIS_WIDTH; x <= AXIS_WIDTH; x++) {
            if (isVisibleAxisPosition(x)) {
                minecraft.setBlock(x, y, 0, block);
                waitForDrawStep();
            }
        }
    }

    private static void drawYAxis(MinecraftRemote minecraft) throws MinecraftRemoteException {
        for (int absoluteY = AXIS_BOTTOM; absoluteY <= AXIS_TOP; absoluteY++) {
            boolean visible = isVisibleYAxisPosition(absoluteY);
            if (visible) {
                String blockId = absoluteY > AXIS_TOP - 5
                        ? "minecraft:glowstone"
                        : "minecraft:sea_lantern";
                minecraft.setBlock(0, relativeY(minecraft, absoluteY), 0, BlockSpec.of(blockId));
                waitForDrawStep();
            }
        }
    }

    private static void drawZAxis(MinecraftRemote minecraft, int y)
            throws MinecraftRemoteException {
        BlockSpec block = BlockSpec.of("minecraft:gold_block");
        for (int z = -AXIS_WIDTH; z <= AXIS_WIDTH; z++) {
            if (isVisibleAxisPosition(z)) {
                minecraft.setBlock(0, y, z, block);
                waitForDrawStep();
            }
        }
    }

    static boolean isVisibleAxisPosition(int coordinate) {
        return coordinate >= 0 || Math.floorMod(coordinate, 2) == 0;
    }

    static boolean isVisibleYAxisPosition(int absoluteY) {
        int coordinate = absoluteY - AXIS_Y_V_ORG;
        return coordinate >= 0 || Math.floorMod(absoluteY - AXIS_BOTTOM, 2) == 0;
    }

    private static void waitForNextServerTick() throws MinecraftRemoteException {
        sleep(50);
    }

    private static void waitForDrawStep() throws MinecraftRemoteException {
        sleep(DRAW_DELAY_MILLIS);
    }

    private static void sleep(int millis) throws MinecraftRemoteException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MinecraftRemoteException("AxisFlat was interrupted", exception);
        }
    }

    private static void printCoordinates(
            BuildContext context, int relativeFloorY, int relativeAxisY, int relativeTopY) {
        var origin = context.origin();
        System.out.printf("build origin（絶対座標）: %s%n", origin);
        System.out.printf(
                "床: 相対y=%d / 絶対y=%d%n",
                relativeFloorY, origin.y() + relativeFloorY);
        System.out.printf(
                "軸の交点: 相対y=%d / 絶対y=%d%n",
                relativeAxisY, origin.y() + relativeAxisY);
        System.out.printf(
                "clear上端: 相対y=%d / 絶対y=%d%n",
                relativeTopY, origin.y() + relativeTopY);
        System.out.printf("整地範囲: 相対x,z=%d..%d%n", -FLAT_WIDTH, FLAT_WIDTH);
        System.out.printf("X/Z軸範囲: 相対x,z=%d..%d%n", -AXIS_WIDTH, AXIS_WIDTH);
        System.out.println("各軸の負領域は破線、正領域は連続線です。");
    }
}
