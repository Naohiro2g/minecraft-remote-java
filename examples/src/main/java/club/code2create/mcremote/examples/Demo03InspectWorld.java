package club.code2create.mcremote.examples;

import club.code2create.mcremote.client.Catalog;
import club.code2create.mcremote.client.MinecraftRemote;
import club.code2create.mcremote.client.MinecraftRemoteException;

/** Finds resource IDs and inspects the paired player's position without changing the world. */
public final class Demo03InspectWorld {

    private Demo03InspectWorld() {}

    public static void main(String[] args) throws MinecraftRemoteException {
        Main.run("inspect");
    }

    static void run(MinecraftRemote minecraft) throws MinecraftRemoteException {
        var pose = minecraft.getPlayerPose();
        var context = minecraft.buildContext();
        var origin = context.origin();
        System.out.println("次元: " + pose.dimension());
        System.out.println("建築原点（絶対座標）: " + origin);
        System.out.println("プレイヤー（原点からの相対座標）: " + pose.position());
        System.out.printf("プレイヤー（絶対座標）: (%.3f, %.3f, %.3f)%n",
                pose.position().x() + origin.x(),
                pose.position().y() + origin.y(),
                pose.position().z() + origin.z());
        System.out.println("接続時の権限: " + minecraft.helloResult().permissions());

        Catalog catalog = minecraft.getCatalog();
        System.out.printf("利用できる種類: block=%d / entity=%d / particle=%d%n",
                catalog.blocks().size(), catalog.entityIds().size(), catalog.particleIds().size());
        String keyword = "lantern"; // 好きな単語に変えてブロック名を探す。
        System.out.println("「" + keyword + "」を含むブロック（最大10件）:");
        catalog.blocks().keySet().stream()
                .filter(id -> id.contains(keyword))
                .sorted()
                .limit(10)
                .forEach(id -> System.out.println("  " + id));
    }
}
