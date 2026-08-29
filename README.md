# Minecraft Remote for Java

Minecraft Remote ProtocolをJavaから利用するための、現行世代のClient Libraryです。

> [!IMPORTANT]
> protocol 23.0.0のPhase B最小縦sliceは、実装と実Minecraftでのlive acceptanceまで完了しています。
> 公開artifactとrelease versionはまだありません。

旧Protocol世代の実装は
[`Naohiro2g/minecraft_remote_java`](https://github.com/Naohiro2g/minecraft_remote_java)に
Classic implementationとして保存されています。このrepositoryはClassicをmodernizeしたものではありません。

## 現在のidentity

- repository: `Naohiro2g/minecraft-remote-java`
- Gradle project: `minecraft-remote-java`
- Java root package: `club.code2create.mcremote.client`
- Maven / Gradle group: `club.code2create.mcremote`
- Maven artifactId: `minecraft-remote-client`
- Java target: 21
- behavioral comparison baseline: Minecraft Remote Protocol 23.0.0／artifact 2300.0.0b6

Gradle project名はrepository／local build identityであり、Maven artifactIdとは別です。公開versionは未設定です。
このbootstrapには`maven-publish`、公開repository、signing、release credentialを設定していません。

## Build

必要なもの:

- JDK 21

repositoryに含まれるGradle wrapperを使います。

```bash
./gradlew build
```

buildはmain source、unit test、通常JAR、sources JARを確認します。

## Examples

開発中のClient Libraryと一緒にbuildされる、実行可能なJava examplesを`examples/`に置いています。
最初の入口は[`MyWorld.java`](examples/src/main/java/club/code2create/mcremote/examples/MyWorld.java)です。

```bash
./gradlew demo
```

接続と必要なpairingの後、Minecraft chatへmessageを送ります。sample一覧、期待結果、world変更範囲、cleanupは
[`examples/README.md`](examples/README.md)を参照してください。

```bash
./gradlew demo --args=list
./gradlew demo --args=hello
./gradlew demo --args=axis-flat
```

`axis-flat`は、blockが既存地形へ埋もれないようbuild originのX/Z±48を整地してから、±40のXYZ軸を描く、
永続的かつ破壊的なsampleです。各軸の負領域は破線、正領域は連続線です。
共有worldでは実行前にbuild originとbackupを確認してください。

## Phase B Client API

現在の最小surfaceは次を提供します。

- TCP上の改行区切りJSON-RPC 2.0
- `hello`とprotocol 23.0.0互換判定
- `auth_required`等のreasonから開始する`auth.pairBegin`／`auth.pairPoll`
- 接続先ごとにscopeされた差し替え可能な`CredentialStore`
- `build.setOrigin`とserver正準build context
- Minecraft chatへの同期`chat.post`
- 構造化block値による`world.setBlock`／`world.setBlocks`／`world.getBlock`
- `AutoCloseable`によるconnection close

```java
import club.code2create.mcremote.client.BlockSpec;
import club.code2create.mcremote.client.BlockValue;
import club.code2create.mcremote.client.CredentialStore;
import club.code2create.mcremote.client.MinecraftRemote;

CredentialStore credentials = CredentialStore.inMemory();

try (MinecraftRemote minecraft = MinecraftRemote.builder("localhost", 25575)
        .credentialStore(credentials)
        .pairingListener(challenge ->
                System.out.println("Minecraftで実行: " + challenge.command()))
        .connect()) {
    minecraft.setBlock(0, 0, 0, BlockSpec.of("minecraft:stone"));
    BlockValue block = minecraft.getBlock(0, 0, 0);
    System.out.println(block);
}
```

`CredentialStore.inMemory()`はprocess終了時にtokenを失います。永続化が必要なapplicationは
`CredentialStore`を実装し、OS credential facility等の保護された保存先を使用してください。libraryは
token file、private endpoint、operator-local設定を既定では作成しません。

決定論的testはloopbackのscripted peerを使い、hello、pairing、block write/read、protocol mismatch、
JSON-RPC error reasonを検証します。protocol 23.0.0／Minecraft 1.21.11の実serverで、enforcement OFFの
helloとblock write/read/restore、およびenforcement ONの初回pairing、token付きhello、block
write/read/restoreを確認済みです。

## 設計の正本

Minecraft Remote固有のProtocol、互換性、bootstrap判断の正本は
[`Naohiro2g/mc-remote-knowledge`](https://github.com/Naohiro2g/mc-remote-knowledge)です。

主な入口:

- `10-protocol/polyglot-client-roadmap_ja.md`
- `16-java-client/README_ja.md`
- decisions `2026-08-29-04`〜`2026-08-29-08`

実装判断は次の順で行います。

1. 現行Protocol SSOT
2. 固定したJava bootstrap baseline上のPython Client Library外部挙動
3. Python内部実装

## 現在の到達点

Phase Bのconnect、hello、protocol negotiation、authentication、初回pairing、block write/read、closeは
固定baseline serverまで通っています。README連動examplesの最初のsliceとして`chat.post`、`world.setBlocks`、
Hello、Set and Read、AxisFlatを追加しています。以後のClient API拡張はknowledge SSOTに従います。

## License

[MIT](LICENSE)
