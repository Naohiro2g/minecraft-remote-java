# Java Clientの対応範囲と再現手順

この文書は、現在のsource checkoutが提供するJava API、そのwire対応、検証依存を示します。公開済みProtocol APIの
正本一覧はknowledgeが所有するため、ここではJava実装の対応範囲だけを扱います。

## 互換性の基線

- Clientが要求するprotocol: `23.0.0`
- 接続判定: serverのmajorが23で、minorが0以上なら受理する。patchは接続判定に使わない
- 実装とfixtureを固定した比較基線: protocol `23.0.0`／artifact `2300.0.0b6`
- 実serverで確認済み: Minecraft `1.21.11`／Paper `1.21.11-132`

同一majorの新しいminorを接続時に受理することと、追加されたすべてのAPIをこのJava版が投影することは別です。
現時点で実装・検証している範囲は次の表です。

Javaの引数・戻り値の型は[生成APIリファレンス](java-api-reference_ja.md)、使い方は
[短い作例](java-examples_ja.md)を参照してください。

## 現在のAPI一覧

| Java API | wire method | 戻り値／役割 |
| --- | --- | --- |
| `MinecraftRemote.Builder.connect()` | `hello` | 接続、互換判定、server-authoritativeな`HelloResult` |
| pairing付き`connect()` | `auth.pairBegin`／`auth.pairPoll` | 認証reasonを受けた場合だけpairingし、token付き`hello`を再試行 |
| `helloResult()` | 接続時の`hello` | handshake結果を参照 |
| `buildContext()` | 接続時の`hello`または`build.setOrigin` | 現在のdimensionとoriginを参照 |
| `setOrigin(x, y, z)` | `build.setOrigin` | server正準の`BuildContext` |
| `postToChat(message)` | `chat.post` | 同期requestを送り、成功resultが`null`であることを検証 |
| `setBlock(...)` | `world.setBlock` | 構造化`BlockSpec`で1 blockを更新 |
| `setBlocks(...)` | `world.setBlocks` | 構造化`BlockSpec`で直方体を更新 |
| `getBlock(...)` | `world.getBlock` | 構造化`BlockValue` |
| `getCatalog()` | `catalog.get` | block state schema、entity ID、particle ID |
| `getPlayerPose()` | `player.getPose` | origin相対の位置と向き |
| `setPlayerPose(...)` | `player.setPose` | server正準の位置と向き |
| `pollEvents(...)` | `events.poll` | cursor、loss情報、型付きevent。新しい互換minorの未知eventは省略 |
| `spawnParticle(...)` | `world.spawnParticle` | data不要particleの受理count |
| `getSign(...)` | `world.getSign` | 両面4行とwax状態 |
| `setSign(...)` | `world.setSign` | 一面または両面を置換 |
| `updateSignLine(...)` | `world.updateSignLine` | 一面の1行を置換 |
| `close()` | TCP close | 接続を閉じる |

`events.poll`は`pickaxe_poke`、`chat_posted`、`projectile_hit`をsealed typeへ投影します。
`world.spawnParticle`はprotocol 23のdata不要形と`force`だけを投影します。

b7以降で追加されたdirection／entity lifecycle、b8のsound／typed particle／receiverは、このcheckoutのAPIに含みません。
Javaはb8／b9 release trainへ無理に同期せず、初回stable公開後に対象releaseを固定して追従します。

rc1時点で調べた新ownerの取得経路と、初回stable後の着手順序は
[Java追従準備](post-latest-plan_ja.md)を参照してください。

## 決定論的な検証

repositoryのGradle wrapperと、miseで選んだJDK 21または25を使います。classfileのtargetは21です。

```bash
mise exec -- ./gradlew test --rerun-tasks
mise exec -- ./gradlew build
```

unit／deterministic testはloopbackのscripted peerを起動し、外部Minecraft serverを必要としません。hello、pairing、
credential lifecycle、protocol mismatch、JSON-RPC error、block操作、catalog、pose、events、particle、signを検証します。

2026-10-21に、miseで選んだJDK 21／25の両方でbuildと全testを実行しました。

| build／testのJDK | 実行コマンド | 結果 |
| --- | --- | --- |
| 21.0.2 | `mise exec java@21 -- ./gradlew build --rerun-tasks` | core 23＋examples 20＝43件PASS |
| 25.0.2 | `mise exec java@25 -- ./gradlew build --rerun-tasks` | core 23＋examples 20＝43件PASS |

どちらもAPIリファレンスの生成・一致確認がPASSし、core／examples JARの全classfileがmajor 65（Java 21）でした。
`mise.toml`は通常利用のJDK 21を選び、CIにはJDK 21／25のmatrixを設定しています。CIのremote実行はまだ行っていません。

## Fixtureの取得元

次の3ファイルは、b6に統合されたScratch owner fixtureをbyte-for-byteでvendorしたtest入力です。

- repository: `Naohiro2g/scratch-editor`
- source commit: `df9264ec355dd722a848df46e96d4b0fc9340ca2`
- source directory: `mc-remote/protocol/test/fixtures/`

| file | SHA-256 |
| --- | --- |
| `events-v23.json` | `31760d267f3c2641042fbe8595fda9c259134a1c05423271a99cb74da1efa9aa` |
| `sign-v23.json` | `7ffb63c264602cba56117eefff1f9604b955df04c5cc655e877772b8ff7cd30e` |
| `spawn-v22.json` | `1120e6c8d41b05b65c916fa96f496b02884123ec7ef59b0a226eea48bebf3abd` |

`Protocol23CapabilitiesTest`はfixtureの値をwire testへ接続し、同時にdigestを固定します。`spawn-v22.json`のshapeは
protocol 23にも変更なく引き継がれています。これらはtest入力であり、Protocol SSOTの代替ではありません。
Javaのruntime／buildはScratchに依存しません。

## 実serverで確認済みの範囲

protocol 23.0.0の実serverで、次を確認済みです。

- enforcement OFF: `hello`、block write／read／restore
- enforcement ON: 初回pairing、token付き`hello`、block write／read／restore
- Linux Secret Service: 保存したsession tokenを別processから再利用し、pairingなしでchat送信
- 代表capability: catalog、player pose read、sign set／update／get、particle、空event ringのpoll

この記録は比較基線の実績です。初回stable追従時は、新しいtooling ownerと取得経路だけを使って対象artifactを固定し、
同等のconsumer検証を改めて行います。

## 2026-10-21のサンプル確認

Javaの要求protocolは`23.0.0`のまま、`23.2.1`を返すMinecraft `1.21.11`の実サーバーへ接続しました。
この確認は現在のサンプルのconsumer検証であり、稼働JARのdigestを固定したrelease gateではありません。

| サンプル | 実サーバーでの結果 |
| --- | --- |
| MyWorld／Hello | pairing・認証後のチャット送信に成功 |
| `inspect` | player pose、権限snapshot、catalog取得に成功。block 1166／entity 157／particle 115 |
| `events` | 空のevent ringへ20回の有限pollを完了。非空eventの目視確認は今回未実施 |
| `01` | 絶対`(302,96,302)`へgold blockを設置し、同じIDを読み返してOK。cleanupの成功後、再設置も成功 |
| `02` | catalog、pose read、看板set／1行update／get、particle accepted=30、event pollに成功。看板cleanup後の再実行も成功 |
| `axis-flat` | X/Z=`252..348`、絶対Y=`63..127`の整地とY=62の床、XYZ軸の描画を完了 |
| メニューの`r` | 同じprocess内のtokenで再接続し、pairingなしでhello・catalog・pose readに成功 |
| プロセスの再起動 | `--save menu`で取得したtokenを保護されたOS storeへ保存し、終了後の別processの`--save hello`がpairingなしで接続・チャット送信に成功 |

初回の書き込みは`build_denied`で拒否されました。Scratchからも再現したとのhuman owner報告があり、
サーバー側の設置数meta設定を修正した後、新しい接続で上記の書き込み確認に成功しました。
拒否される前にチャット送信やcatalog取得が成功していても、block setter固有のポリシーを満たす証明にはなりません。
拒否された操作をJava側で迂回・自動再試行する処理は追加していません。

human ownerの画像では、XYZ軸、金ブロック、看板のgold太字とaquaの行を確認しました。
particleの目視表示は画像から追加主張しません。画像では長い看板文言が表示幅で切れていたため、
試作ソースの先頭2行を`McRemote`／`Java Client`へ短縮しています。画像は短縮前ですが、
後続の実サーバー実行では短縮した4行の読み返しにも成功し、現在の看板は短縮後の文言です。
整地・XYZ軸、金ブロック、看板は観察用に残しています。`axis-flat-clear`の実サーバー確認は今回行っていません。

`ExampleProtocolCompatibilityTest`は、既存・追加サンプルの本体をloopback TCPの23.2.1 peerへ接続します。
既存のevents／sign fixtureの応答を消費し、chatの`null`、block write/read、sign／particle、event cursor、
整地のrequest分割、拒否後のメニュー継続、同じtokenでの再接続を検証します。
サーバーの権限設定やPaper上の描画・物理動作は、このdeterministic testの検証範囲に含めません。
今回のfull buildではcore 23件＋examples 20件、計43件がPASSしました。

```bash
./gradlew :examples:test --tests '*ExampleProtocolCompatibilityTest'
./gradlew build
```

## B9 freeze前の契約監査

2026-10-05にknowledge `main@900f6f4b8027d265a62ba7f139d4f3b1bbe78100`まで静的照合しました。

- helloの`session`／`player`／`permissions`／`server`省略許容はknowledge issue #9で解決済みです。
- 認証reasonごとのcredential処理と再pairing境界は現行contractに一致します。
- protocol version判定はmajor一致、server minor以上、patch不問で一致します。
- block、catalog、pose、protocol 23 event、particle、signの現行投影に追加の契約差分は見つかりませんでした。
- 未知IDのJSON-RPC errorを現在のrequestへ誤適用する実装差分を見つけ、未知IDを成功／errorとも破棄するよう修正しました。
- `chat.post`成功resultは`2026-10-03-01`どおり`null`を検証します。
- `2026-10-03-02`に従い、新しい互換minorの未知event typeを省略し、serverが返した`through_sequence`とloss counterを
  保持します。同じminorで定義されていないevent typeはcontract違反として`ProtocolException`にします。
- freeze前に返すべき契約疑問の棚卸しは完了し、この2件以外に残っていません。
