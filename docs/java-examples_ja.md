# Javaの短い作例

[READMEの最初の実行](../README.md)の次に、値の読み方や小さな操作を試すための作例です。
実行できるclassは[サンプル一覧](../examples/README.md)、正確なシグネチャは
[Java APIリファレンス](java-api-reference_ja.md)から探せます。

## 接続する

次は標準のJava APIだけで接続を組み立てる例です。JDK 21／25はREADMEのmise手順で選びます。
JDKのclassに加え、このcheckoutのクライアントがclasspathに必要です。
単独ファイルを`java`で実行する場合の依存設定は含んでいません。初めて動かす場合はREADMEのGradleサンプルを使ってください。

```java
import club.code2create.mcremote.client.CredentialStore;
import club.code2create.mcremote.client.MinecraftRemote;
import club.code2create.mcremote.client.MinecraftRemoteException;

public class HelloMinecraft {
    public static void main(String[] args) throws MinecraftRemoteException {
        // 同じstoreを次の接続にも渡せば、このprocess内の期限内tokenを再利用できます。
        CredentialStore credentials = CredentialStore.inMemory();
        try (MinecraftRemote minecraft = MinecraftRemote.builder("localhost", 25575)
                .credentialStore(credentials)
                .pairingListener(challenge -> System.out.println(challenge.command()))
                .connect()) {
            minecraft.postToChat("Javaからこんにちは！");
        }
    }
}
```

`connect()`が成功すると利用可能な接続を返します。`try`を抜けると接続を閉じます。
接続先、pairingの表示、OSへのtoken保存を自分で組み立てたくない場合は、
サンプルの`Main`がこれらを受け持ちます。

以下の断片は、接続済みの`minecraft`を使います。`MyWorld.run`の中に書いて試せます。
必要な`import`はファイル先頭へ加えてください。

## ブロックを置き、stateを読む

**絶対座標`(302, 96, 302)`の1ブロックを置き換えます。** 許可された作業場所で試してください。

```java
import club.code2create.mcremote.client.BlockSpec;
import java.util.Map;
```

```java
minecraft.setOrigin(300, 0, 300);
minecraft.setBlock(2, 96, 2,
        BlockSpec.of("minecraft:oak_log", Map.of("axis", "x")));
var block = minecraft.getBlock(2, 96, 2);
System.out.println(block.blockId()); // minecraft:oak_log
System.out.println(block.state());  // axisなど、サーバーが返す正準state
```

座標は建築原点から相対です。ブロックIDは文字列で指定し、stateは`Map`の値に文字列・boolean・数値を使います。
置き換える操作は省略したstateを既定値にするため、既存stateの一部分だけを更新する操作ではありません。
除去するときは同じ原点で`minecraft.setBlock(2, 96, 2, BlockSpec.of("minecraft:air"))`を実行します。
元のブロックやblock entityのデータまで復元する操作ではありません。

## 接続先で使えるブロックを探す

[Demo03InspectWorld](../examples/src/main/java/club/code2create/mcremote/examples/Demo03InspectWorld.java)は、worldを変えずに
現在位置とcatalogを表示します。

```java
var catalog = minecraft.getCatalog();
catalog.blocks().keySet().stream()
        .filter(id -> id.contains("lantern"))
        .sorted()
        .limit(10)
        .forEach(System.out::println);
```

`catalog.blocks().get("minecraft:oak_log")`の`states()`は許容値、`defaultState()`は既定値です。
実際に接続したサーバーの定義を読みます。Java版にはPython版のような補完用定数ファイルの自動生成はまだありません。

## 看板を飾る

既に設置した看板の、前面4行を置き換える例です。`Sign`と`List`をimportします。

```java
import club.code2create.mcremote.client.Sign;
import java.util.List;
```

```java
minecraft.setSign(4, 96, 4, Sign.Content.front(List.of(
        Sign.LineSpec.styled("ようこそ", "gold", Sign.Decoration.BOLD),
        Sign.LineSpec.plain("Javaの実験場"),
        Sign.LineSpec.plain(""),
        Sign.LineSpec.plain(""))));
minecraft.updateSignLine(4, 96, 4, Sign.Face.FRONT, 2,
        Sign.LineSpec.styled("準備できました", "aqua"));
var sign = minecraft.getSign(4, 96, 4);
sign.front().forEach(line -> System.out.println(line.text()));
```

看板ブロックは先に`setBlock`で設置します。`front`は前面だけ、`both`は両面を更新します。
1行の番号は`0..3`です。看板の設置からparticleの表示までを試すコードは
[Demo02Protocol23Tour](../examples/src/main/java/club/code2create/mcremote/examples/Demo02Protocol23Tour.java)にあります。

## イベントを有限回読む

[Demo04Events](../examples/src/main/java/club/code2create/mcremote/examples/Demo04Events.java)は、約10秒で終了する例です。
メニューで`events`を選んでから、Minecraftでツルハシを持ってブロックを右クリックするか、チャットを投稿してください。

```java
long cursor = 0;
for (int i = 0; i < 3; i++) {
    var result = minecraft.pollEvents(cursor, 16);
    result.events().forEach(System.out::println);
    cursor = result.throughSequence();
}
```

これは3回だけ、待ち時間を入れずに読む短い例です。操作を待ちながら観察する場合はDemo04を使います。
返ったeventが空でも、サーバーが返す`throughSequence()`へcursorを進めます。
`overflowDroppedTotal()`などは取りこぼしの累積値です。新しい接続ではcursorを0から始めます。
eventの位置を建築に使う場合は、eventの次元と原点が現在の`buildContext()`と一致するか確認してください。

## 拒否理由を調べる

```java
import club.code2create.mcremote.client.RemoteCallException;
```

```java
try {
    minecraft.postToChat("こんにちは！");
} catch (RemoteCallException exception) {
    System.out.println(exception.reason().orElse("unknown"));
}
```

`RemoteCallException`はサーバーが返した拒否です。`TransportException`は接続・通信の失敗、
`ProtocolException`は互換判定や応答の形の不一致です。いずれも`MinecraftRemoteException`を継承します。
通信失敗時には、world操作が既に実行されたか分からない場合があります。書き込みを自動で繰り返さないでください。
