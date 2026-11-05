# 多言語化 E1：Javaの対応表と開発体験

2026-11-04のJava担当の確認資料です。knowledgeのPhase E／Fと`2026-10-23-01`に従い、Javaのstable追従、
E1のJava列、Phase Dの評価をまとめます。Protocol契約や他componentの列の正本はknowledgeです。

## 参照identity

- knowledge contract commit: `6d408c789aa72cffa2c547fe5ae97c3aa56e6d93`
- knowledge contract path: `10-protocol/wire-format-design_ja.md`、`10-protocol/versioning-design_ja.md`、
  `10-protocol/polyglot-phase-ef-plan_ja.md`、`16-java-client/README_ja.md`
- Java sourceの基点: `main@66814b992e629deec60028d4af0d978212477526`。本確認資料と同じ変更に含まれる実装を対象とする
- McRemote: [v2321.0.0](https://github.com/Naohiro2g/McRemote/releases/tag/v2321.0.0)
  → `525c31823a8efb736463aec471c2756089546391`
- tooling: [v2321.0.0](https://github.com/Naohiro2g/minecraft-remote-tooling/releases/tag/v2321.0.0)
  → `6447f6e8cbfbeeb9d229990b5c4f6e82a5875666`
- 両releaseの公開日時・draft=false・prerelease=falseと、tag解決先をGitHub APIで個別に照合した。
  `target_commitish`の文字列は固定commitの代用にしていない
- 対象protocol: `23.2.1`。Javaの要求最低版は`23.0.0`を維持。bootstrapのb6は履歴の比較基線
- fixtureはtoolingの固定sourceから取得。[取得元・digest](../src/test/resources/protocol-fixtures/README.md)を参照。
  配布JARのdownload／実行は今回の検証に含めていない

## E1：methodごとのJava列

行はknowledgeの`10-protocol/api-reference-metadata.json`（同knowledge commit）の32 methodをすべて含みます。
「あり」は記載したClient APIへの投影があることを示し、全server reasonや実機での全経路を検証済みとは意味しません。
privateな汎用requestによる呼び出し可能性を、公開APIの投影とは数えません。

実装は[テストと同じsource](../src/main/java/club/code2create/mcremote/client/MinecraftRemote.java)、
テストは[core test directory](../src/test/java/club/code2create/mcremote/client/)を参照してください。

| Protocol method | Java | API／範囲 | 根拠 |
| --- | --- | --- | --- |
| `hello` | あり | `Builder.connect()` → `HelloResult` | `MinecraftRemoteTest`：接続・不一致・認証 |
| `connection.flush` | なし | 対応するpublic APIなし | `MinecraftRemote.java`の公開APIを静的確認 |
| `build.setDimension` | なし | 対応するpublic APIなし | `MinecraftRemote.java`の公開APIを静的確認 |
| `build.setOrigin` | あり | `setOrigin` → `BuildContext` | `MinecraftRemoteTest.changesAndRetainsTheServerCanonicalBuildOrigin` |
| `world.setBlock` | あり | `setBlock`／`BlockSpec` | `MinecraftRemoteTest.connectsAndReadsAndWritesOneStructuredBlock` |
| `world.setBlocks` | あり | `setBlocks`／`BlockSpec` | `MinecraftRemoteTest.postsOneChatMessageAndFillsOneStructuredBlockRegion` |
| `world.getBlock` | あり | `getBlock` → `BlockValue` | `MinecraftRemoteTest.connectsAndReadsAndWritesOneStructuredBlock` |
| `world.getBlocks` | なし | 対応するpublic APIなし | `MinecraftRemote.java`の公開APIを静的確認 |
| `world.getHeight` | なし | 対応するpublic APIなし | `MinecraftRemote.java`の公開APIを静的確認 |
| `world.getSign` | あり | `getSign` → `Sign.Value` | `Protocol23CapabilitiesTest.consumesTheOwnerEventsSignAndParticleFixtures` |
| `world.setSign` | あり | `setSign`／`Sign.Content` | 同上 |
| `world.updateSignLine` | あり | `updateSignLine`／`Sign.LineSpec` | 同上 |
| `player.getPos` | なし | 対応するpublic APIなし | `MinecraftRemote.java`の公開APIを静的確認 |
| `player.setPos` | なし | 対応するpublic APIなし | `MinecraftRemote.java`の公開APIを静的確認 |
| `player.getPose` | あり | `getPlayerPose` → `PlayerPose` | `Protocol23CapabilitiesTest.projectsCatalogAndPlayerPoseIntoJavaTypes` |
| `player.setPose` | あり | `setPlayerPose` → `PlayerPose` | 同上。liveでのplayer移動は未主張 |
| `player.getDirection` | なし | 対応するpublic APIなし | `MinecraftRemote.java`の公開APIを静的確認 |
| `player.setDirection` | なし | 対応するpublic APIなし | `MinecraftRemote.java`の公開APIを静的確認 |
| `world.spawnEntity` | なし | 対応するpublic APIなし | `MinecraftRemote.java`の公開APIを静的確認 |
| `world.getNearbyEntities` | なし | 対応するpublic APIなし | `MinecraftRemote.java`の公開APIを静的確認 |
| `entity.getPose` | なし | 対応するpublic APIなし | `MinecraftRemote.java`の公開APIを静的確認 |
| `entity.setPose` | なし | 対応するpublic APIなし | `MinecraftRemote.java`の公開APIを静的確認 |
| `entity.getDirection` | なし | 対応するpublic APIなし | `MinecraftRemote.java`の公開APIを静的確認 |
| `entity.setDirection` | なし | 対応するpublic APIなし | `MinecraftRemote.java`の公開APIを静的確認 |
| `entity.remove` | なし | 対応するpublic APIなし | `MinecraftRemote.java`の公開APIを静的確認 |
| `world.spawnParticle` | 部分 | data不要particle、offset／speed／count／force。typed dataとreceiverはなし | `Protocol23CapabilitiesTest`：spawn-v22 case |
| `world.playSound` | なし | 対応するpublic APIなし | `MinecraftRemote.java`の公開APIを静的確認 |
| `world.playBlockSound` | なし | 対応するpublic APIなし | `MinecraftRemote.java`の公開APIを静的確認 |
| `world.strikeLightning` | なし | 対応するpublic APIなし | `MinecraftRemote.java`の公開APIを静的確認 |
| `chat.post` | あり | `postToChat`、同期成功resultはnull | `SharedCompatibilityFixtureTest`：B9-C01〜C07 |
| `events.poll` | あり | `pollEvents`／`EventPollResult`／sealed `MinecraftEvent` | `SharedCompatibilityFixtureTest`：B9-E01〜E26。既存events-v23は`Protocol23CapabilitiesTest` |
| `catalog.get` | あり | `getCatalog` → `Catalog`。自動cache／生成定数は別項目 | `Protocol23CapabilitiesTest`：catalog schema／pose |

`auth.pairBegin`／`auth.pairPoll`はmetadataの32行には含まれませんが、接続能力として下表にも含めます。
`player.getPos`／`setPos`に相当する目的の一部をpose APIで達成できても、独立したwire methodへの投影は「なし」です。

## E1：method以外の能力

| 能力 | Java | 根拠と境界 |
| --- | --- | --- |
| TCP framing／JSON-RPC | あり | `JsonRpcConnection`：改行区切りUTF-8、同期request／response、id対応、timeout／close |
| WebSocket／browser | なし | TCPのみ |
| protocol negotiation | あり | `ProtocolVersion`、`MinecraftRemoteTest.rejectsAnIncompatibleProtocolAdvertisedByHello`。major一致、server minor以上、patch不問 |
| authentication／pairing | あり | `Builder.connect`内のpairBegin／pairPoll、session tokenでhelloを再試行。`MinecraftRemoteTest`のpairing 3 test |
| session credential UX | あり | coreの`CredentialStore` SPI、examplesのOS store選択。認証reasonで該当tokenのみ削除し、権限／版／通信errorで保持。OS利用不能時memory。long-lived一般公開は別事項 |
| request／notification／flushと順序 | 部分 | 同期requestと一接続の順序あり。notification、flush、DEBUG／TRACE／FASTの公開APIなし |
| events cursor／loss | あり | B9-E01〜E26。数値cursorでの再取得、snapshotを渡した際の累積値の後退検出。未知typeも共通項目とsequenceを検証して省略 |
| error reason | あり | `RemoteCallException`でcode／message／data／reasonを保持。`JsonRpcConnectionTest`で未知idのerrorを破棄。reason別の全server経路を網羅したとの主張はしない |
| build context | 部分 | origin設定とhelloのdimension／originを保持。dimensionの変更APIはなし |
| BlockValue／BlockSpec | あり | 読み取り正準値と書き込み入力を別recordで投影。`BlockSpecTest`、block write/read test |
| catalog／constants | 部分 | serverのcatalogとhashを型付き値として取得。生成定数、catalogの永続cache、server切替時の定数生成はなし |
| hello world_constants | 部分 | `HelloResult.worldConstants()`で受け取ったMapを公開。`y_sea`専用型や生成sourceはなし |
| epoch／reconnect | 部分 | 接続ごとにhello／build contextを取得し、examplesの`r`で再接続。snapshot／cursorは新接続で0から。entity handleは未実装 |
| WireScope observer | なし | 専用observer連携なし |

## Phase D：Javaの開発体験

評価方法はsource・README・生成API・実行可能examplesの静的確認と、同じcheckoutのbuild／testです。
今回IDEの対話操作、初学者による操作、実サーバーでの新規試験は行っていません。

| 観点 | 観察 | 改善先 |
| --- | --- | --- |
| resourceの発見 | `Demo03InspectWorld`で接続先catalogのblock／entity／particleを調べ、blockのstates／defaultStateを読める。サンプルをTCP peerで実行するtestあり | catalogを一覧するだけでなく、propertyと許容値を使って入力を作る短い例へ |
| 型安全性 | `BlockSpec`／`BlockValue`、`PlayerPose`、sign record、sealed event unionあり。block IDとpropertyはString／Mapで、catalogへの存在・許容値はコンパイル時に決まらない | generated sourceやbuilderの要否をE2で分類。Pythonの定数方式を先に固定しない |
| 補完 | Javaの型・メソッド・enumを補完する足場とJavadocあり。server固有resource ID／state名の補完を提供する仕組みはなし | IDEでの実操作と、catalogに基づく生成source等の候補を別途評価 |
| 編集と実行 | README → MyWorld → menu → 作例 → API。miseでJDKを選び、Gradle wrapperがexamplesを一緒にbuild／testする | 新しい学習者の実操作で確認。Maven artifactによる導入はまだなし |
| documentation／LLM | 公開signatureからAPI表を生成し、buildが一致を検査。serverのcatalogを取得するコードはあるが、LLMへ渡す専用exportはなし | catalogの安全なexportの要否を検討。実tokenやprivate targetを教材へ含めない |

推奨状態：source checkoutで代表capabilityを使い、型とcatalogを調べられる段階です。
Phase C全体の完了、一般向け推奨教材への昇格、IDE補完の実機評価済みは主張しません。
DXの不足は上表の改善先として捕捉し、独立したPhase D gateにはしません。

## 検証と残り

- `SharedCompatibilityFixtureTest`：owner fixtureのB9-C01〜C07、B9-E01〜E26の33 case。
  正常／拒否、未知event省略後のcursor・loss保持、既知payload、statefulな後退をTCPで検証する。
  bounded／unboundedの両snapshot overloadを使う
- JDK 21.0.13：`mise exec java@21 -- ./gradlew generateApiReference build javadoc` がPASS。
  JDK 25.0.5：`mise exec java@25 -- ./gradlew build` がPASS。
  各core 56＋examples 20＝76件、failure／error／skipは0。API生成一致、core／examplesの全classfile major 65も確認。
  Javadocは既存のコメント不足の警告を伴って生成成功。今回差分のremote CIは未実行
- wire文書は前回参照knowledgeとのbyte一致を確認。既存fixture 3本もbyte一致。
  2026-10-21の実サーバー観測は履歴として保持し、stableのexact JARへの新規live PASSにはしない
- F1のhello／auth／blockの共有scripted caseの新設、F2の各clientでの採用、F3のlive-auto pulseは未実施。
  接続先・操作範囲がまだ渡されていないため、shared環境には接続していない
- tooling固定commitにはfixture directoryの`LICENSE`がない。SSOT `2026-10-23-01`③のMIT化の未反映を返す。
  今回のJavaによるconsumer検証を、移管全体の完了やPhase E／Fの合格にしない

## 確定搬送票

- 搬送元 repo: `Naohiro2g/minecraft-remote-java`
- 搬送元 surface: Codex
- 搬送元 branch/commit: 本資料を含む`main`のcommit（実装の基点は`66814b992e629deec60028d4af0d978212477526`）
- 作成日: 2026-11-04
- 種別: その他（既決方針の実装報告・E1入力・未反映の捕捉）
- 決定: 新しい横断決定はなし。Javaの追従対象sourceをstable `2321.0.0`に固定し、fixture取得元をtoolingへ切り替えた。
  要求最低protocolは実装capabilityに足りる`23.0.0`を維持。Javaは未知eventを検証後に省略する既存方針を継続する
- 理由: `2026-09-30-04`、`2026-10-05-01`、`2026-10-23-01`のJava consumer追従とE1集約を進めるため
- 却下案（3件まで）: 新たな却下案なし
- 影響: Javaのevent応答検証とsnapshot継続API、Demo04、対応範囲とfixture provenance。Protocol／tooling実装は変更していない
- 根拠/検証: 上の参照identity、method表、DX表、owner 33 caseのunit/deterministic。正式な新規live evidenceはなし
- 既に変更した実装/文書: `MinecraftRemote.java`、`Protocol23CapabilitiesTest`、`SharedCompatibilityFixtureTest`、
  `Demo04Events`、fixture READMEとJSON、生成API・作例・互換性文書・追従計画、本資料
- ナレッジ着地希望: `10-protocol/protocol-inventory_ja.md`のJava列とDX評価へ集約し、`16-java-client/README_ja.md`の現在地を更新。
  toolingのfixture directoryのMIT LICENSE配置を、既決事項の未反映としてhub NOTESへ捕捉する
- 捕捉 cleanup: knowledge着地照合後、local `NOTES_ja.md`の2026-11-04行を着地参照へ整理する
- 着地後の確認戻り先: このrepoのCodex session。搬送時は、本資料を含むpush済みcommitのSHAを添える
