package club.code2create.mcremote.examples;

import club.code2create.mcremote.client.CredentialStore;
import club.code2create.mcremote.client.MinecraftRemote;
import club.code2create.mcremote.client.MinecraftRemoteException;
import java.util.Locale;
import java.util.Scanner;

/** Command-line entry point for the examples in this repository. */
public final class Main {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 25575;

    private Main() {}

    public static void main(String[] args) throws MinecraftRemoteException {
        String selection = args.length == 0 ? "my-world" : args[0];
        if ("list".equals(selection)) {
            printExamples();
            return;
        }
        if ("menu".equals(selection)) {
            runMenu();
            return;
        }
        run(selection);
    }

    static void run(String selection) throws MinecraftRemoteException {
        try (MinecraftRemote minecraft = connect()) {
            printConnection(minecraft);
            runSelected(selection, minecraft);
        }
    }

    private static void runMenu() throws MinecraftRemoteException {
        try (MinecraftRemote minecraft = connect(); Scanner scanner = new Scanner(System.in)) {
            printConnection(minecraft);
            while (true) {
                printExamples();
                System.out.print("実行する項目を入力してください（xで終了）: ");
                String selection = scanner.nextLine().trim();
                if ("x".equalsIgnoreCase(selection)) {
                    return;
                }
                runSelected(selection, minecraft);
            }
        }
    }

    private static MinecraftRemote connect() throws MinecraftRemoteException {
        String host = System.getenv().getOrDefault("MCREMOTE_HOST", DEFAULT_HOST);
        int port = parsePort(System.getenv("MCREMOTE_PORT"));

        System.out.printf("Minecraft Remoteへ接続しています: %s:%d%n", host, port);
        return MinecraftRemote.builder(host, port)
                .credentialStore(CredentialStore.inMemory())
                .pairingListener(challenge -> {
                    System.out.println();
                    System.out.println("ペアリングが必要です。Minecraftで次を実行してください:");
                    System.out.println();
                    System.out.println("    " + challenge.command());
                    System.out.println();
                    System.out.printf(
                            "有効期限: %d秒。ペアリング完了を待っています...%n",
                            challenge.expiresIn().toSeconds());
                })
                .connect();
    }

    private static int parsePort(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException("MCREMOTE_PORT must be between 1 and 65535");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("MCREMOTE_PORT must be an integer", exception);
        }
    }

    private static void printConnection(MinecraftRemote minecraft) {
        var hello = minecraft.helloResult();
        System.out.printf(
                "接続しました: Minecraft %s / protocol %s / origin %s%n%n",
                hello.minecraftVersion(), hello.protocol(), minecraft.buildContext().origin());
    }

    private static void runSelected(String rawSelection, MinecraftRemote minecraft)
            throws MinecraftRemoteException {
        String selection = rawSelection.toLowerCase(Locale.ROOT);
        switch (selection) {
            case "my-world", "myworld" -> MyWorld.run(minecraft);
            case "00", "hello" -> Demo00Hello.run(minecraft);
            case "01", "set-and-read", "setblock" -> Demo01SetAndReadBlock.run(minecraft);
            case "01-cleanup", "setblock-cleanup" -> Demo01SetAndReadBlock.cleanup(minecraft);
            case "axis-flat", "axis" -> AxisFlat.prepare(minecraft);
            case "axis-flat-clear", "axis-clear" -> AxisFlat.clearAxes(minecraft);
            default -> {
                System.out.println("不明な項目です: " + rawSelection);
                printExamples();
            }
        }
    }

    private static void printExamples() {
        System.out.println();
        System.out.println("Minecraft Remote for Java examples");
        System.out.println("  my-world        MyWorld.java（学習者が編集する入口）");
        System.out.println("  00 / hello       Minecraft chatへメッセージを送る");
        System.out.println("  01 / setblock    ブロックを置いて読み返す");
        System.out.println("  01-cleanup       01が置いたブロックをairに戻す");
        System.out.println("  axis-flat        許可範囲内を整地してXYZ軸を描く（worldを変更）");
        System.out.println("  axis-flat-clear  XYZ軸だけ消す（平面は残る）");
        System.out.println();
    }
}
