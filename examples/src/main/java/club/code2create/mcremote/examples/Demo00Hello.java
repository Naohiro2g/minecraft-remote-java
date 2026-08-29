package club.code2create.mcremote.examples;

import club.code2create.mcremote.client.MinecraftRemote;
import club.code2create.mcremote.client.MinecraftRemoteException;

/** Minimum example: make one result visible in Minecraft chat. */
public final class Demo00Hello {

    private Demo00Hello() {}

    public static void main(String[] args) throws MinecraftRemoteException {
        Main.run("hello");
    }

    static void run(MinecraftRemote minecraft) throws MinecraftRemoteException {
        String message = "Hello from Minecraft Remote for Java!";
        minecraft.postToChat(message);

        System.out.println("期待結果: Minecraft chatに次の文が表示される");
        System.out.println("  " + message);
    }
}
