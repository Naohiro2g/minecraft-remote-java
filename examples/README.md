# Minecraft Remote for Java examples

このdirectoryは、現在のJava Client Library sourceと一緒にbuildされる、README連動の実行可能sampleです。
最初にMinecraft上で結果を見て、その後にJava codeを書き換えるための入口として使います。

## 必要なもの

- JDK 21
- protocol 23.0.0に対応したMinecraft Remote server
- 既定の接続先は`127.0.0.1:25575`

接続先を変える場合は、repositoryへ設定を保存せず環境変数を使います。

```bash
MCREMOTE_HOST=example.local MCREMOTE_PORT=25575 ./gradlew demo
```

blockを変更するsampleは、他の検証と重なりにくいよう既定で絶対座標`(300, 0, 300)`をbuild originにします。
複数人で使う場合は、学習者ごとにoriginをずらせます。

```bash
MCREMOTE_ORIGIN_X=500 MCREMOTE_ORIGIN_Y=0 MCREMOTE_ORIGIN_Z=300 ./gradlew demo --args=axis-flat
```

## 最初の一歩

[`MyWorld.java`](src/main/java/club/code2create/mcremote/examples/MyWorld.java)を開き、次を実行します。

```bash
./gradlew demo
```

最初に、session tokenの扱いを既定値なしで選びます。

```text
session tokenを保護されたOS資格情報storeへ保存しますか？
  save    次回の実行で再利用する
  memory  このprocess内だけで使用する
選択してください [save/memory]:
```

認証が必要な場合は、terminalにコピー可能なpairing commandを表示して待機します。

```text
/mcremote pair 123-456
```

成功すると、Minecraft chatに`Hello from MyWorld.java!`と表示されます。`MyWorld.java`の`message`を
書き換え、もう一度実行してください。

`save`を選ぶと、macOS Keychain、Windows Credential Manager、Linux Secret Serviceのうち利用可能な保護storeへ
接続先単位のsession tokenを保存します。次回も`save`を選べば、期限内tokenを使ってpairingなしで接続できます。
保護storeを利用できない場合は平文fileへfallbackせず、そのprocess内のmemoryだけを使うため、次回もpairingが必要です。

対話を省略する場合はpolicyを明示します。

```bash
./gradlew demo --args='--save hello'
./gradlew demo --args='--no-save hello'
```

`--no-save`は保存済みtokenを含む永続storeをread／write／clearしません。共有PCや一時的な実行で使用してください。
選択した接続先のlocal tokenを削除するには、次を単独で実行します。この操作はserverへ接続せず、別端末のtokenを
revoke／logoutしません。

```bash
./gradlew demo --args=--forget
```

token、private endpoint、operator-local設定をsource tree、command argument、通常logへ書かないでください。

## Sample一覧

```bash
./gradlew demo --args=list
```

| ID | 実行 | 期待結果 | world変更 | cleanup |
| --- | --- | --- | --- | --- |
| MyWorld | `./gradlew demo` | 編集したmessageがMinecraft chatに表示される | playerへの一時表示のみ | 不要 |
| 00 Hello | `./gradlew demo --args=hello` | 固定messageがMinecraft chatに表示される | playerへの一時表示のみ | 不要 |
| 01 Set and Read | `./gradlew demo --args=01` | gold blockを置き、同じblock IDをterminalへ表示する | 1 block、永続 | `./gradlew demo --args=01-cleanup` |
| 02 Protocol 23 Tour | `./gradlew demo --args=02` | catalog／player pose／eventsをterminalへ表示し、看板とparticleをMinecraftで確認する | oak sign 1 block、永続。particleは一時表示 | 不要 |
| AxisFlat | `./gradlew demo --args=axis-flat` | 整地した空間に色分けしたXYZ軸を表示する | 97×97の床と絶対Y=63..127、永続 | 軸だけなら`./gradlew demo --args=axis-flat-clear`。完全復元はworld backupが必要 |

すべてのblock座標は、example runnerが`build.setOrigin`で選んだbuild originからの相対座標です。AxisFlatは
Python版と同じ幾何を使い、絶対Y=62に床を作り、X/Z±48、絶対Y=63..127をairへ置換します。仮想原点は
絶対Y=96、X/Z軸は相対`-40..40`、Y軸は絶対`63..127`です。各軸の負領域を1 blockおきの破線、正領域を
連続線にするため、符号と原点を視覚的に区別できます。

serverのrequest work上限を越えないよう、97×97の各層は最大64×64のtileへ分け、上から1 block高ずつ
clearします。

Protocol 23 Tourは、server-authoritative catalogの件数とhash、player pose、event ringの先頭3件をterminalへ
表示します。その後、build originから相対`(4, 96, 4)`（origin Yが既定の0の場合）へoak signを置き、前面を
set／1行update／getして、上にflame particleを表示します。`player.setPose`はClient APIに含まれますが、sampleが
学習者を不意にteleportしないよう、このTourではposeのreadだけを行います。

AxisFlatの色は次のとおりです。

- X軸: diamond block
- Y軸: sea lantern
- Y軸上端5 block: glowstone
- Z軸: gold block
- 原点: emerald block

AxisFlatは意図的にworldを大きく変更します。共有worldでは管理者が指定したbuild originとbackupを確認してから
実行してください。

## Javaから直接実行

Gradleのapplication entry pointは`Main`ですが、各classにも`main()`があります。IDEのRun操作、またはmain classを
指定できるJava実行環境から、`MyWorld`、`Demo00Hello`、`Demo01SetAndReadBlock`、
`Demo02Protocol23Tour`、`AxisFlat`を直接実行できます。

通常JARへruntime dependencyを埋め込んでいないため、現時点の主導線はGradle wrapperです。

## Sample metadata

- minimum protocol: 23.0.0
- client: このcheckoutからbuildされる未公開Java Client Library
- confirmed Minecraft target: 1.21.11
- confirmed Paper baseline: 1.21.11-132
- confirmed credential backend: Linux Secret Service（初回pairing後、別processで期限内session tokenを再利用）
- execution mode: 同期request
- output target: real Minecraft world
- implementation position: Java client thin projection＋Java user code
