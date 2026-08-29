# Minecraft Remote for Java

Minecraft Remote ProtocolをJavaから利用するための、現行世代のClient Libraryです。

> [!IMPORTANT]
> 現在はbootstrap段階です。まだMinecraftへ接続するClient APIは実装されていません。

旧Protocol世代の実装は
[`Naohiro2g/minecraft_remote_java`](https://github.com/Naohiro2g/minecraft_remote_java)に
Classic implementationとして保存されています。このrepositoryはClassicをmodernizeしたものではありません。

## 現在のidentity

- repository: `Naohiro2g/minecraft-remote-java`
- Gradle project: `minecraft-remote-java`
- Java root package: `club.code2create.mcremote.client`
- Gradle logical group: `club.code2create.mcremote`
- Java target: 21
- behavioral comparison baseline: Minecraft Remote Protocol 23.0.0／artifact 2300.0.0b6

Gradle project名はlocal build identityです。Maven artifactIdや公開versionを意味しません。
このbootstrapには`maven-publish`、公開repository、signing、release credentialを設定していません。

## Build

必要なもの:

- JDK 21

repositoryに含まれるGradle wrapperを使います。

```bash
./gradlew build
```

buildはmain source、unit test、通常JAR、sources JARを確認します。

## 設計の正本

Minecraft Remote固有のProtocol、互換性、bootstrap判断の正本は
[`Naohiro2g/mc-remote-knowledge`](https://github.com/Naohiro2g/mc-remote-knowledge)です。

主な入口:

- `10-protocol/polyglot-client-roadmap_ja.md`
- `16-java-client/README_ja.md`
- decisions `2026-08-29-04`〜`2026-08-29-07`

実装判断は次の順で行います。

1. 現行Protocol SSOT
2. 固定したJava bootstrap baseline上のPython Client Library外部挙動
3. Python内部実装

## 次の到達点

最初の縦sliceは、TCP＋line-delimited JSON-RPC 2.0、`hello`、protocol negotiation、authentication、
初回pairing、`world.setBlock`／`world.getBlock`を実Minecraft worldまで通すことです。

## License

[MIT](LICENSE)
