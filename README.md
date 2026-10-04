# Minecraft Remote for Java

> [!NOTE]
> このREADMEを含む公開文書は日本語を正本とします。方針は
> [`LANGUAGE_POLICY.md`](https://github.com/Naohiro2g/mc-remote-knowledge/blob/main/LANGUAGE_POLICY.md)
> を参照してください。

Minecraft Remote ProtocolをJavaから利用するためのClient Libraryです。JDK 21とprotocol 23.0.0を基線に、
接続、pairing、world操作、catalog、player pose、events、particle、signを型付きAPIとして提供します。

> [!IMPORTANT]
> 現在はsource checkoutから使う開発版です。公開artifactとrelease versionはまだありません。

## 最初の3ステップ

1. JDK 21とprotocol 23対応のMinecraft Remote serverを用意します。
2. repositoryのGradle wrapperでbuildします。
3. demoを起動し、必要ならMinecraft内で表示されたpairing commandを実行します。

```bash
./gradlew build
./gradlew demo
```

成功するとMinecraft chatに`Hello from MyWorld.java!`と表示されます。最初に編集するcodeは
[`MyWorld.java`](examples/src/main/java/club/code2create/mcremote/examples/MyWorld.java)です。

## Client API

現在の主な入口は`MinecraftRemote`です。

- `hello`、protocol互換判定、`auth.pairBegin`／`auth.pairPoll`
- 接続先ごとにscopeされた`CredentialStore`
- `build.setOrigin`、`chat.post`
- `world.setBlock`／`world.setBlocks`／`world.getBlock`
- `catalog.get`
- `player.getPose`／`player.setPose`
- `events.poll`とprotocol 23の型付きevent
- `world.spawnParticle`
- `world.getSign`／`world.setSign`／`world.updateSignLine`

Java method、wire method、対応範囲の一覧は
[`docs/compatibility_ja.md`](docs/compatibility_ja.md)にまとめています。

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
`CredentialStore`を実装し、OS credential facilityなどの保護された保存先を使ってください。libraryはtoken file、
private endpoint、operator-local設定を既定では作成しません。OS依存のstarter実装は`examples` subprojectにあります。

## Examples

sample一覧、期待結果、world変更範囲、cleanupは[`examples/README.md`](examples/README.md)を参照してください。

```bash
./gradlew demo --args=list
./gradlew demo --args='--save hello'
./gradlew demo --args='--no-save axis-flat'
./gradlew demo --args=--forget
```

`--save`はmacOS Keychain、Windows Credential Manager、Linux Secret Serviceのうち利用可能な保護storeを使います。
利用できない場合は平文fileを作らずmemoryへ縮退します。`--no-save`は永続storeをread／write／clearしません。
`--forget`は選択した接続先のlocal session tokenだけを削除し、serverへ接続しません。

`axis-flat`はbuild originのX/Z±48を整地し、±40のXYZ軸を描く永続的で破壊的なsampleです。共有worldでは
build originとbackupを確認してから実行してください。

## 対応状況と検証

- protocol基線: `23.0.0`
- behavioral comparison baseline: `2300.0.0b6`
- Java target: 21
- Maven coordinateの確定部分: `club.code2create.mcremote:minecraft-remote-client`
- 公開version、publish plugin、registry、release workflow: 未確定

決定論的testはloopbackのscripted peerを使います。Scratch owner由来のprotocol fixture 3本は、取得元commitと
SHA-256を固定したtest入力としてのみ利用し、Scratchのruntime／buildには依存しません。再現手順、fixture一覧、
実serverで確認済みの範囲は[`docs/compatibility_ja.md`](docs/compatibility_ja.md)にあります。

Javaはb8／b9 release trainの参加componentではありません。初回stableがGitHubのLatestになった後、その公開releaseと
新しいtooling owner／取得経路を固定して追従・再検証します。

## Project identity

- repository: `Naohiro2g/minecraft-remote-java`
- Gradle project: `minecraft-remote-java`
- Java root package: `club.code2create.mcremote.client`
- Maven / Gradle group: `club.code2create.mcremote`
- Maven artifactId: `minecraft-remote-client`

Gradle project名はrepository／local build identityであり、Maven artifactIdや公開versionではありません。

旧Protocol世代の実装は
[`Naohiro2g/minecraft_remote_java`](https://github.com/Naohiro2g/minecraft_remote_java)に
Classic implementationとして保存されています。

## 設計の正本

Minecraft Remote固有のProtocol、互換性、release flowの正本は
[`Naohiro2g/mc-remote-knowledge`](https://github.com/Naohiro2g/mc-remote-knowledge)です。

- [`10-protocol/`](https://github.com/Naohiro2g/mc-remote-knowledge/tree/main/10-protocol)
- [`16-java-client/`](https://github.com/Naohiro2g/mc-remote-knowledge/tree/main/16-java-client)

## License

[MIT](LICENSE)
