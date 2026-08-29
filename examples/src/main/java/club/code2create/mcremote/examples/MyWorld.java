package club.code2create.mcremote.examples;

import club.code2create.mcremote.client.MinecraftRemote;
import club.code2create.mcremote.client.MinecraftRemoteException;

/** The first file for a learner to edit. */
public final class MyWorld {

    private MyWorld() {}

    public static void main(String[] args) throws MinecraftRemoteException {
        Main.run("my-world");
    }

    static void run(MinecraftRemote minecraft) throws MinecraftRemoteException {
        String message = "Hello from MyWorld.java!";

        // 最初は、このmessageを好きな言葉に書き換えて実行してみよう。
        minecraft.postToChat(message);

        System.out.println("Minecraft chatへ送信しました: " + message);
    }
}
