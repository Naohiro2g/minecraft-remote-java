package club.code2create.mcremote.examples;

import club.code2create.mcremote.client.MinecraftRemote;
import club.code2create.mcremote.client.MinecraftRemoteException;

/** A finite event loop: advance using the server cursor, including when no known event is returned. */
public final class Demo04Events {

    private Demo04Events() {}

    public static void main(String[] args) throws MinecraftRemoteException {
        Main.run("events");
    }

    static void run(MinecraftRemote minecraft) throws MinecraftRemoteException {
        System.out.println("約10秒間、ツルハシでの右クリックやチャット投稿を観察します。");
        long cursor = 0; // この接続のevent ringの先頭から読む。
        for (int i = 0; i < 20; i++) {
            var result = minecraft.pollEvents(cursor, 16);
            for (var event : result.events()) {
                System.out.printf("#%d %s%n", event.sequence(), event);
            }
            // 知らないeventが省略されても、サーバーが返すcursorを使って先へ進む。
            cursor = result.throughSequence();
            System.out.printf("cursor=%d / loss totals: overflow=%d capacity=%d discarded=%d%n",
                    cursor, result.overflowDroppedTotal(), result.capacityDroppedTotal(),
                    result.explicitlyDiscardedTotal());
            if (i < 19) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new MinecraftRemoteException("イベントの観察を中断しました", exception);
                }
            }
        }
        System.out.println("イベントの観察を終了しました。");
    }
}
