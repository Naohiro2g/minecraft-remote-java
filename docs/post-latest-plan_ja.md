# 初回stable後のJava追従計画

2026-11-06更新。多言語化のPhase E／Fへ渡すJava側の作業です。対象releaseとJava対応表・DX評価は
[E1確認資料](polyglot-e1_ja.md)、現在のAPIは[対応範囲](compatibility_ja.md)を参照してください。

## 仕上げた範囲

- 初回stableのMcRemote `v2321.0.0` → `525c31823a8efb736463aec471c2756089546391`、
  tooling `v2321.0.0` → `6447f6e8cbfbeeb9d229990b5c4f6e82a5875666`をGitHub APIで固定した。
  両releaseともdraft／prereleaseはfalse。toolingの同名tagは個別に解決した。
- fixtureの現行取得元を`minecraft-remote-tooling/packages/protocol/test/fixtures/`へ切り替えた。
  既存events／sign／spawnは移管前のvendor copyとbyte一致し、digestを維持した。
- `chat-event-compat-v23.2.1.json`をbyte-for-byteで取り込み、33 caseすべてをJavaのTCP testへ接続した。
  未知eventの共通項目とsequence、既知eventのexact field、指定cursorとの関係を検証する。
  前回snapshotを渡すAPIでlatest sequenceと各累積loss counterの後退も検出する。
- E1のJava列と、Phase Dのcatalog／型／補完の評価を同じ[確認資料](polyglot-e1_ja.md)にまとめた。
  Phase Dを独立gateにする旧順序は、knowledge `2026-10-23-01`①②に従って更新した。

要求protocolの最低版は`23.0.0`を維持する。このsliceは既存capabilityの検証であり、新しいminorのcapabilityを
追加していない。追従対象のstable `2321.0.0`、接続時に要求するprotocol、将来のMaven公開versionを分ける。
bootstrapのb6比較基線は履歴として保持する。

## 再利用した検証と今回の検証

前回（2026-10-21）に読んだknowledge `70e772bcbcd977157c582774ce3d68536ac85b3c`と、今回の
`6d408c789aa72cffa2c547fe5ae97c3aa56e6d93`で、`10-protocol/wire-format-design_ja.md`は全体がbyte一致した。
既存fixture 3本もrc1／stableの同じtooling sourceとbyte一致した。これを根拠に、README導線、block／sign／
particle等の既存wire投影と、過去の実サーバーでの観測を保持する。stableの稼働JARに対するlive PASSへは読み替えない。

event decoderとpublic API、Demo04を変更したため、今回のbuildはcoreとexamplesを実行する。
サンプルの過去の実サーバー試験一式は自動的にやり直さない。今回のsource／fixture検証に配布JARは使っていない。
新しいlive-auto pulseは、接続先と許可範囲をcoordinatorから受け取って実施する。F3のlive条件は未達である。

## 次の局所slice

この順序はJava repoの案であり、横断contractではない。

| 順 | 作業 | 検証と得られる保証 |
| --- | --- | --- |
| 1 | `connection.flush`、DEBUG／TRACE／FAST | 同一connectionの送信順序、block setterへのmode適用、TRACE pacing、FASTのnotification、timeout後の完了不明とconnection回収 |
| 2 | dimension／build context、bulk read等 | 既存originと座標の意味を維持し、後続world／entity操作の前提を整える |
| 3 | directionとentity handleからentity lifecycleへ | epoch、session permission、失効とerror。lightningはworld変更範囲を別に確認 |
| 4 | typed particle、receiver、sound | 型付きpayloadと対象、共有fixtureの正常／拒否case、resource IDの入力と正準出力 |

各sliceは現行wireとowner fixtureを読み、targeted deterministic testで進める。
`flush`の成功はnotification個別成功の集約でも、画面描画・chunk保存の完了でもない。
mode名とdelayはwireへ送らない。実装の進捗に合わせ、E1のJava列も更新する。

## knowledgeへ返す事項

E1のJava列・DX評価はknowledge `8c81c0f115127189de117c3848d1e254f79571c9`のinventoryとJava spokeへ着地確認済み。
[E1確認資料](polyglot-e1_ja.md)はJava側の根拠として保持する。
fixtureのMIT LICENSEはtooling `21a2a9a0aed8b4d9c715da46616aad86f1174b4c`で反映済み。
Javaのfixture取得元もそのcommitへ固定し、LICENSEをcopyへ添えた。使用中4本のbytesとSHA-256は変わっていない。
Java側の検証成功をtooling移管全体やPhase E／Fの完了判定にしない。

TypeScriptはF1の範囲がそろってから開始し、C#はTypeScriptのPhase Bが通ってから開始する。
C#／Unity内部の先行準備は別担当の範囲である。
Maven公開、WireScope連携は引き続き別途判断する。

## 参照

今回のknowledge commitは`6d408c789aa72cffa2c547fe5ae97c3aa56e6d93`。

- [Javaのrelease追従](https://github.com/Naohiro2g/mc-remote-knowledge/blob/6d408c789aa72cffa2c547fe5ae97c3aa56e6d93/16-java-client/README_ja.md)
- [Phase E／F](https://github.com/Naohiro2g/mc-remote-knowledge/blob/6d408c789aa72cffa2c547fe5ae97c3aa56e6d93/10-protocol/polyglot-phase-ef-plan_ja.md)
- [wire §3.5、§5.4](https://github.com/Naohiro2g/mc-remote-knowledge/blob/6d408c789aa72cffa2c547fe5ae97c3aa56e6d93/10-protocol/wire-format-design_ja.md)
