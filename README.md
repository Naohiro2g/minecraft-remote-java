# Minecraft Remote for Java

> [!NOTE]
> 公開文書は日本語を正本とします。[言語方針](https://github.com/Naohiro2g/mc-remote-knowledge/blob/main/LANGUAGE_POLICY.md)をご覧ください。

[マイクラリモコン](https://mc-remote.com/)をJavaから操作するクライアントです。
チャットを送る、ブロックを積む、看板を書く、ツルハシの操作を読む、といったプログラムを作れます。

このリポジトリは **`minecraft-remote-java`（ハイフン）** です。
名前が似た旧版の[`minecraft_remote_java`（アンダースコア）](https://github.com/Naohiro2g/minecraft_remote_java)は、
旧Protocol世代の履歴保管用です。現行版のコードを開くときは、ハイフンのフォルダを選んでください。

## 最初のメッセージを送る

### 1. MinecraftとJavaを用意する

- Java版Minecraftで、[McRemote](https://github.com/Naohiro2g/McRemote/releases)を導入したサーバーへ入ります。
  サーバーの用意は[自分のPC・LANで使う案内](https://mc-remote.com/#lan)を参照してください。
  公式箱庭を使う場合は[公式サイト](https://mc-remote.com/)で接続先と対応Minecraft版を確認します。
- プログラムを動かすPCにはGitと[mise](https://mise.jdx.dev/getting-started.html)を用意します。
  **JDKは21または25に対応し、導入・切り替えはmiseを正準の手順とします。**
  WindowsではGit Bashから以下のコマンドを実行できます。Gradle本体のインストールは不要です。

### 2. サンプルを取得する

```bash
git clone https://github.com/Naohiro2g/minecraft-remote-java.git
cd minecraft-remote-java
mise trust
mise install
mise exec -- java -version
mise exec -- javac -version
```

`mise.toml`の既定はJDK 21です。JDK 25を使う場合は、実行コマンドを
`mise exec java@25 -- ./gradlew ...`にします。[miseのJava設定](https://mise.jdx.dev/lang/java.html)も参照できます。

既定では同じPCのサーバーへ接続します。別のPCや箱庭を使う場合は、実行するターミナルで設定します。

```bash
export MCREMOTE_HOST=example.local
export MCREMOTE_PORT=25575
```

`example.local`を案内された接続先へ置き換えてください。接続設定をソースに書き込む必要はありません。

### 3. 実行して、ゲーム内で承認する

```bash
mise exec -- ./gradlew --console=plain demo --args=hello
```

初回は必要な依存ファイルをダウンロードします。最初にtokenの扱いを`save`（OSの保護されたstoreへ保存）か
`memory`（今回のprocess内だけ）から選びます。session tokenの有効期限は**標準設定で発行から2時間**です。
`save`なら、その2時間以内はプロセスを起動し直してもtokenを再利用できます。
ターミナルに表示される`/mcremote pair NNN-NNN`を、**120秒以内**にMinecraftのチャットへ貼り付けて実行してください。
ゲーム内に **Hello from Minecraft Remote for Java!** と表示されれば成功です。
このサンプルはブロックを変更しません。

`memory`や`--no-save`では認証tokenをこの実行中だけ保持します。プロセスを終了して起動し直すと、再度pairingが必要です。
続けて試すときは、最初から次のメニューを使うと、同じ接続でサンプルを切り替えられます。

```bash
mise exec -- ./gradlew --console=plain demo --args=menu
```

`hello`、`inspect`などを入力します。`r`は取得済みtokenで再接続し、`x`は終了します。
有効なtokenが保持されていれば、再接続だけを理由にpairingをやり直しません。

## 自分の言葉に変える

[`MyWorld.java`](examples/src/main/java/club/code2create/mcremote/examples/MyWorld.java)を開き、次のメッセージを変えます。

```java
String message = "Hello from MyWorld.java!";
minecraft.postToChat(message);
```

保存して実行します。変更したソースはGradleがコンパイルします。

```bash
mise exec -- ./gradlew --console=plain demo --args=my-world
```

## 次に試すこと

| やってみたいこと | メニューの入力 | 読むコード |
| --- | --- | --- |
| ブロック名と現在位置を調べる | `inspect` | [Demo03InspectWorld](examples/src/main/java/club/code2create/mcremote/examples/Demo03InspectWorld.java) |
| 金ブロックを置いて読み返す | `01` | [Demo01SetAndReadBlock](examples/src/main/java/club/code2create/mcremote/examples/Demo01SetAndReadBlock.java) |
| 看板を飾り、particleを出す | `02` | [Demo02Protocol23Tour](examples/src/main/java/club/code2create/mcremote/examples/Demo02Protocol23Tour.java) |
| ツルハシの右クリック・チャットを観察する | `events` | [Demo04Events](examples/src/main/java/club/code2create/mcremote/examples/Demo04Events.java) |
| 作業用の平面とXYZ軸を描く | `axis-flat` | [AxisFlat](examples/src/main/java/club/code2create/mcremote/examples/AxisFlat.java) |

ブロックを変更するサンプルは、既定で絶対座標`(300, 0, 300)`を建築原点にします。
**整地は97×97の床と高さ63〜127の空間を変更します。** 実行前に
[変更範囲と片付け](examples/README.md)を確認し、管理者から許可された場所で試してください。

[短いJava作例](docs/java-examples_ja.md)から、読み取り値・state・看板・event cursorの使い方へ進めます。

## APIを調べる

- [JavaクライアントAPIリファレンス（試作）](docs/java-api-reference_ja.md): Javaのメソッド、引数、戻り値の型。
- [Protocol API一覧](https://mc-remote.com/api/): サーバーが受け取る操作と応答。
- [Javaの対応範囲・検証結果](docs/compatibility_ja.md): 実装済みの機能と確認した環境。

Java版は現在、同期TCP接続、ブロック、catalog、player pose、看板、data不要particle、event pollを提供します。
Python版にある高速建築モード、entity操作、sound、WireScope連携などは今後の作業です。

## 認証情報を次回も使う

```bash
mise exec -- ./gradlew --console=plain demo --args='--save menu'
```

`--save`は利用可能なOSの保護された資格情報storeへtokenを保存し、次回も発行から2時間以内のtokenを使います。
macOS Keychain、Windows Credential Manager、Linux Secret Serviceに対応するadapterをサンプルに用意しています。
利用できない場合はメモリだけで動き、その旨を表示します。平文ファイルへの自動保存はしません。

接続先のlocal tokenを削除するには`mise exec -- ./gradlew demo --args=--forget`を使います。
サーバー側でtokenを失効させる操作とは別です。詳しくは[サンプルの認証設定](examples/README.md#認証情報の扱い)を参照してください。

## 困ったとき

| 状況 | 確認すること |
| --- | --- |
| 接続できない | `MCREMOTE_HOST`・`MCREMOTE_PORT`、McRemoteの起動、ファイアウォールを確認します。Minecraftの参加用portとは別です |
| `pair_expired` | 承認待ちコードの有効期限は120秒です。新しく表示されたコードで承認してください。承認後に取得するsession tokenの有効期限は2時間です |
| `build_denied` | 建築原点と許可範囲、1回の設置数上限を確認します。rc1のLuckPermsでは`mcr.build.blocks`の設定も必要です |
| `permission_denied` | 管理者へ操作権限とオンライン／オフラインの許可を確認します |
| Javaのコンパイルで止まる | `mise exec -- java -version`と`mise exec -- javac -version`でJDK 21／25を確認します。`javac`が見つからない場合はJREだけでなくJDKが必要です |

問題の報告・相談は[プロジェクトのfeedback窓口](https://github.com/Naohiro2g/mc-remote-knowledge/issues)へ。
tokenやprivateな接続先は貼り付けないでください。

## 開発する

```bash
mise exec -- ./gradlew build
mise exec -- ./gradlew generateApiReference
mise exec -- ./gradlew javadoc
```

JDK 25へ切り替えてbuildする場合:

```bash
mise install java@25
mise exec java@25 -- ./gradlew build
```

build・test・サンプルの実行にはmiseで選んだJDKを使います。どちらでbuildしても、生成するclassfileのtargetはJava 21です。

API一覧はJavaソースと[日本語の説明](tools/api-reference/descriptions_ja.properties)から生成します。
`build`は生成結果との一致も確認します。Javadocは`build/docs/javadoc/index.html`へ出力されます。
[初回stable後の作業計画](docs/post-latest-plan_ja.md)も参照できます。

Maven公開は準備中です。確定しているcoordinateは`club.code2create.mcremote:minecraft-remote-client`で、
公開version・publish経路はまだありません。設計の正本は[knowledge](https://github.com/Naohiro2g/mc-remote-knowledge)にあります。

[MIT License](LICENSE)
