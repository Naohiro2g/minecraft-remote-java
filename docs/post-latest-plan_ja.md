# 初回stable後のJava追従準備

2026-10-21時点のJava repository内の作業計画です。実装順序は局所案であり、Protocolや他componentのrelease計画を
変更するものではありません。現在のJava対応範囲は[互換性の基線](compatibility_ja.md)を参照してください。

## 目的と開始条件

初回stableがGitHubのLatestになった後、その公開releaseを固定し、移管先のtoolingだけからfixtureを取得してJavaを
追従・再検証します。根拠はknowledgeの次の文書です。今回読んだknowledge commitは
`70e772bcbcd977157c582774ce3d68536ac85b3c`です。

- [Javaのrelease追従方針](https://github.com/Naohiro2g/mc-remote-knowledge/blob/70e772bcbcd977157c582774ce3d68536ac85b3c/16-java-client/README_ja.md)
- [移管計画のJavaの位置づけ](https://github.com/Naohiro2g/mc-remote-knowledge/blob/70e772bcbcd977157c582774ce3d68536ac85b3c/10-protocol/protocol-tooling-migration-plan_ja.md)
- [多言語Client Libraryロードマップ](https://github.com/Naohiro2g/mc-remote-knowledge/blob/70e772bcbcd977157c582774ce3d68536ac85b3c/10-protocol/polyglot-client-roadmap_ja.md)

rc1はまだpre-releaseです。今行うのは公開identityと取得経路の調査、fixture照合、既存testの再実行、後続sliceの準備です。
fixtureの取得元とJavaの要求protocolの変更は、初回stable公開後のsliceで扱います。

README・生成APIリファレンス・実行可能サンプルの試作と、現行23.2.1サーバーでの代表操作の確認は先行して実施済みです。
Latest後に契約内容・wire・fixtureが同じと照合できた範囲は、今回の検証結果を再利用します。
対象releaseのtag／commitの固定は軽い確認として残し、同じ試験一式を自動的にやり直しません。
fixture取得元の切り替えと、未接続の追加共有fixtureをJavaのtestへ接続する作業は未実施として区別します。

## rc1で確認した足場

GitHub APIでReleaseの公開状態とtagの解決先commitを確認しました。`target_commitish: main`を固定commitの代用には
していません。以下は準備の参照値であり、初回stableの採用identityではありません。

| 対象 | 公開tag | 解決先commit | 状態 |
| --- | --- | --- | --- |
| McRemote | `v2321.0.0rc1` | `192a777ae1d484bcc9b58fcc7c58d6aed1a374af` | 公開済み、pre-release |
| minecraft-remote-tooling | `v2321.0.0rc1` | `6447f6e8cbfbeeb9d229990b5c4f6e82a5875666` | 公開済み、pre-release |

toolingの取得先は`Naohiro2g/minecraft-remote-tooling`の`packages/protocol/test/fixtures/`です。
[固定commitの案内](https://github.com/Naohiro2g/minecraft-remote-tooling/blob/6447f6e8cbfbeeb9d229990b5c4f6e82a5875666/packages/protocol/README.md)
を読み、この経路から既存3本を一時領域へ取得し、Javaのvendor copyとのbyte一致とSHA-256一致を確認しました。

| 既存fixture | rc1 ownerとの照合 |
| --- | --- |
| `events-v23.json` | byte一致。digestは[fixture一覧](../src/test/resources/protocol-fixtures/README.md)の値を維持 |
| `sign-v23.json` | byte一致。同上 |
| `spawn-v22.json` | byte一致。同上 |

追加候補も同じ固定commitから取得し、次のdigestと収録範囲を確認しました。まだJavaのtest入力へ取り込んでいません。

| fixture | SHA-256 | 使用するslice |
| --- | --- | --- |
| `chat-event-compat-v23.2.1.json` | `eead244a7340c315cb63e1e77ede0fe9cf48162c06fedbeef36d941950d5000d` | chat成功result、未知event、cursor／lossの共有契約照合 |
| `direction-lightning-v23.1.json` | `586d24bf40136eec31f1827f23ef5b317f15100a17a635d7fe9f165e0af40dce` | direction、entity handle、lightning |
| `entity-particle-v23.2.1.json` | `0319ff8373a96798cf5e9e421a804729fd33a0fa53e96adc12c93ed7994777ae` | entity lifecycle、typed particle、sound、resource ID |

toolingは過去protocolのfixtureも履歴として保持します。ファイル名の版を一括で改名せず、対象contractに対応した入力を
選びます。fixture内の`knowledge_contract`は成立時点の参照であり、Javaが最新に読んだknowledge commitとは分けて記録します。

Java `35f582d1dde85b08451902429727a6df792af321`＋今回のworking tree差分は、miseで選んだJDK 21／25の両方で
core 23件＋examples 20件、計43件がPASSしました。実サーバーのprotocol 23.2.1／Minecraft 1.21.11で、
整地、block write/read、sign、particle受理、空event poll、別processからの期限内token再利用まで確認しました。
検証範囲と画像の位置づけは[対応範囲と検証結果](compatibility_ja.md)を参照してください。

## Latest後の最初のslice

1. 最新のruntime protocolと関連SSOTを読み直す。現行Protocol世代の初回stable公開を確認し、対象Releaseの
   tag／解決先commit／protocolを記録する。旧Protocol世代のstableを返し得る`releases/latest`だけでは判断しない。
2. 採用するtoolingの公開tagと解決先commitを固定する。McRemoteと同名tagであることを前提にしない。
   fixtureは固定commitから取得し、取得元pathとSHA-256を記録する。使う配布artifactがあれば、そのbytes／digestも固定する。
3. 既存3本を改めて照合し、fixture READMEと互換性文書の取得元を新ownerへ更新する。履歴のScratch取得元と
   b6比較基線は履歴として残す。通常のbuild／CIはvendorしたlocal copyを使い、runtime fetchを追加しない。
4. 対象releaseのchat／event共有fixtureを取り込み、既存の`chat.post`の`null`検証、未知eventの省略、
   `through_sequence`とloss counterの保持をowner caseへ接続する。現行の手書きtestが全caseを検証済みとは扱わない。
   statelessな戻り値検証と、cursor／前回counterを伴うstatefulな検証のAPI上の責任を整理する。
5. 照合で差分がなかった既存範囲は今回のPASSを再利用し、取得元切り替え・追加fixture接続・実装変更に必要なtestを実行する。
   差分がある場合は、その影響に応じてcore／examplesのtestや実サーバー確認を選ぶ。
   採用release、取得元、digest、再利用した根拠、新たに検証したcase、未対応のcaseを報告し、文書の主張を揃える。

このsliceはowner切替と既存投影の再検証を担当します。要求protocolの引き上げは、新たに実装するcapabilityの最低minorと
照合して決めます。公開artifact版の数字をJavaの要求protocolやMaven版へそのまま転記しません。

## Phase Cの実装順序案

最初のsliceの結果を見て、次の順に小さく実装します。個々のJava API名や型は各sliceで決めます。

| 順 | 作業 | 検証と得られる保証 |
| --- | --- | --- |
| 1 | `connection.flush`、DEBUG／TRACE／FAST | 同一connectionの送信順序、block setterだけのmode適用、TRACE pacing、FASTのnotification、timeout時の完了不明とconnection回収をdeterministic testで確認する |
| 2 | dimension／build context、bulk read等の不足を整理 | 既存のoriginと座標の意味を保ち、次のworld／entity操作の前提を整える。既存owner fixtureと現行wireの該当範囲を確認して着手する |
| 3 | directionとentity handleを足場にentity lifecycleへ進む | handleのconnection epoch、session permission、失効とエラーを含めて型へ投影する。lightningはworld変更範囲を独立して検証する |
| 4 | typed particle、receiver、sound | Javaの型でpayloadと対象を表し、owner fixtureの正常／拒否caseとresource IDの入力・正準出力を確認する |

execution modeのcontractは
[wire §3.5](https://github.com/Naohiro2g/mc-remote-knowledge/blob/70e772bcbcd977157c582774ce3d68536ac85b3c/10-protocol/wire-format-design_ja.md)
を参照します。`flush`成功はnotification個別成功の集約でも、画面描画・chunk保存の完了でもありません。
mode名やdelayをwireへ載せず、既存の同期APIを使う学習者の動作を確認しながら追加します。

各sliceはまずtargeted unit／deterministicと共有fixtureで検証します。対象artifactの実server検証では、接続・pairing／
session再利用・既存代表capabilityのconsumer pulseを行い、追加したworld変更は範囲と復元方法を決めてから試します。
shared環境はcoordinatorから接続先と操作範囲を受け取って使います。初回pairingや目視確認が必要な分はlive-humanへ分けます。

Phase Cの到達を確認した後は、Phase Dのdeveloper experience評価（catalogからのresource発見、Javaの型、IDE補完、
READMEからの編集と実行）へ進みます。その後にPython／Java／Protocol比較、既存conformance資産の一般化を扱います。

## 別に判断する事項

- Maven公開はversion、POM、Javadoc JAR、signing、publish経路のSSOT確定後に扱います。
- JavaのWireScope連携は共通observer contractを調べる段階から始めます。hub NOTESで初回stable後の優先とされた
  WireScope lifecycle／状態表示の検討を確認し、Java固有の待機・終了contractを先に固定しません。
- Javaの追従結果からProtocolやtoolingへ返す発見は、local `NOTES_ja.md`に捕捉してknowledgeへ搬送します。
  Javaの再検証の成功を、移管全体の完了判定には使いません。
