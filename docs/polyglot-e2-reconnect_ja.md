# 多言語化：待ち方とつなぎ直し（E2の③⑤）

knowledgeの`00-hub/polyglot-e2-reconnect-instructions_ja.md`の「共通」と「Java」への返却の資料です。
E2の③⑤（`2026-11-11-01`）と`2026-11-12-05`をJavaへ入れ、F1をtooling `335c604`へ固定し直し、F1 v2をCIへつなぎました。

## 参照identity

| 対象 | identity |
| --- | --- |
| knowledge | 依頼が入ったmain `125812b47bae32120f56febc4d30058ab0fe8248`。着手した時のmainも同じcommit |
| fixtureの取得元 | minecraft-remote-tooling `335c604f859d6dfad04f0434bfa4eb6445761430`（PR 10のmerge）の`packages/protocol/test/fixtures/` |
| Java | このbranchの元は`main@223ef287f1cb17a7b2cdbf619d08d0ffd7a17e37`（PR 4のmerge） |

## fixtureの取得元

| | 前 | 後 |
| --- | --- | --- |
| commit | `36ebe9e63e4f0afcf949eae998832f68209b8119` | `335c604f859d6dfad04f0434bfa4eb6445761430` |
| 持ち方 | copy 12本＋`LICENSE` | copy 14本＋`LICENSE`。変わった4本をcopyし直し、v2の2本を足した |

bytesとSHA-256は、11本すべて依頼の票の表と一致しました。`36ebe9e`から変わったもの、加わったものは次のとおりです。
ほかのframing、blocks、errors、`block-value-v22.json`、`LICENSE`はbytesが同じです。

| file | 前 bytes / SHA-256 | 後 bytes / SHA-256 |
| --- | --- | --- |
| `client-bootstrap-hello-v23.2.1.json` | 20,913 / `78297f05eb4352b0dabffab3242e933c1e68cb192bbb6d242019c7956c809d57` | 26,046 / `caf24ad5382e411ba54172b05d6020fc59fc429dc0251ff28db3908b4189784e` |
| `client-bootstrap-pairing-v23.2.1.json` | 35,577 / `5864c23ef3f31af6134d36770cab80b569b708b35fd3fdbed69fb6db195e3aeb` | 45,943 / `19230b310c7482adc740095d5604110937ebaa7803a0fed4c294083a9e63a460` |
| `client-bootstrap-close-v23.2.1.json` | 14,884 / `a030cc72aa0e4f41575cb15610b1e06e941d3eab07157ce47f0eafe744475154` | 20,879 / `2abb6f48942e1dc7bc42014ba016c3d08a6632c59445c048b008a4c411ea1e30` |
| `client-bootstrap-v1.schema.json` | 10,916 / `8c3e4ff6e7b5b0cf438d358578096579dafab699a40df9cd56de7b9513837413` | 11,521 / `970dd68999edc14a4dcc2b67ab2f712e830b7841b2ec4548fee66af117fd281b` |
| `client-bootstrap-operations-v23.2.1.json`（v2） | — | 66,335 / `8309f5847ad69aa2d82fcbcb1726918c3a69ba875e04e8f38678352b06b7f7a1` |
| `client-bootstrap-v2.schema.json` | — | 19,468 / `8ae1c7313d01fa0fa24f4f2079ba453129dc16b678342df6f7ef66b9255d1381` |

## fileごとのcase数とPASS数

| file | case | PASS | todo |
| --- | ---: | ---: | ---: |
| `client-bootstrap-framing-v23.2.1.json` | 7 | 7 | 0 |
| `client-bootstrap-hello-v23.2.1.json` | 12 | 12 | 0 |
| `client-bootstrap-pairing-v23.2.1.json` | 12 | 12 | 0 |
| `client-bootstrap-blocks-v23.2.1.json` | 12 | 12 | 0 |
| `client-bootstrap-errors-v23.2.1.json` | 3 | 3 | 0 |
| `client-bootstrap-close-v23.2.1.json` | 8 | 8 | 0 |
| v1 計 | 54 | 54 | 0 |
| `client-bootstrap-operations-v23.2.1.json`（v2） | 12 | 10 | 2 |

v1にskipは無い。v2のtodoは、JUnitではskipとして出し、PASSに数えない。

| todoのcase | 理由 |
| --- | --- |
| F1-OP-06 | FAST（id無しのnotification）と`connection.flush`がJavaに無い。⑤Bは、FASTとflushを入れるときに満たす |
| F1-OP-11 | entity handle、接続のepochに結んだevent cursor、catalogのcacheがJavaに無く、`seed_epoch_state`で用意する状態が無い。server／session／`catalogHash`の変化の知らせは、unit testで確かめた（下の表） |

`todoCasesAreExactlyTheOnesNeedingMissingFeatures`が、FAST、`connection.flush`、`seed_epoch_state`を使うcaseとtodoの集合が
同じであることを確かめるので、todoを黙って増やせない。

## v2のrunnerの読み方

`ClientBootstrapOperationsFixtureTest`は、v2のopをJavaのAPIへ次のように写す。

| op | Javaのrunner |
| --- | --- |
| `app_connect`、`app_call` | 呼び出しごとのthreadで`Builder.connect()`、`getBlock`、`setBlock`、`setDimension`、`setOrigin`を始め、完了を待たない。次のstepの前に、すべての呼び出しが応答・lock・時計のどれかを待つ状態になるまで待つ |
| `expect_connect` | loopbackのpeerがacceptした順に名前を付ける。clientの接続（`JsonRpcConnection`）は、作られた順に対応させる |
| `expect_send`、`peer_send` | 1行のJSONを読む、書く。`peer_send`の後は、clientがそのframeを受け取るまで待つ |
| `peer_eof` | peerの`shutdownOutput` |
| `socket_error`、`liveness_lost` | peerがSO_LINGER 0でcloseし、RSTを送る。JavaのTCP keepaliveが相手の消えたことを見つけたときも、受信のreadが失敗する同じ経路で接続が閉じる |
| `connect_error`（`terminal`） | その接続を開く入力の前に、peerが待ち受けを閉じて接続を拒む。つなぎ直しの待ちは仮想の時計で進むので、呼び出しが終わるまで時計を進める（つなぎ直しの規則そのものを最初の失敗で諦める形にしない） |
| `advance_clock` | 仮想の時計を進め、`receive_idle`は`DEFAULT_RESPONSE_TIMEOUT`（15分）、`pairing_ttl`は`expires_in`を長さにする。進めた後に、clientのtimeoutの判定を一度呼ぶ |
| `observe`と最後の`expected` | 書いた欄だけを比べる。`pending_ids`は集合で比べる |

`calls`の`source`と`outcome`は、Javaの結果から次のように読む。`app_connect`の成功は`client`、ほかの呼び出しの成功は
`server`と、そのrequestを送った接続。`RemoteCallException`は`server_error`（`error`はwireのerrorと同じ値）、
`CompletionUnknownException`は`completion_unknown`、`NotSentException`は`not_sent`（`cause`は`reason()`の小文字）、
`PairingExpiredException`は`pairing_expired`（`equivalent_to`は`pair_expired`）。`events`は`ReconnectEvent`から読み、
`reconnected`と`events_lost`は毎回、`server_changed`と`session_changed`は変わったときに出す。

## ③⑤の入れた形

### API

| 項目 | Java |
| --- | --- |
| 応答の待ち方（③） | `Builder.responseTimeout(Duration)`、既定は`MinecraftRemote.DEFAULT_RESPONSE_TIMEOUT`＝15分。`MinecraftRemote.setResponseTimeout(Duration)`で、今の接続とつなぎ直した接続の長さを変えられ、応答を待っているrequestにもすぐ効く。`readTimeout`は非推奨の別名として残した |
| 数え方 | 応答待ちがある間、その接続で最後に受信した時（応答待ちが無い所から送った時を含む）から数える。ほかの応答が届くたびに数え直す |
| 尽きたとき | その接続を閉じ、応答待ちのrequestをすべて`CompletionUnknownException`にする。次の呼び出しはつなぎ直す |
| 生存確認（③、⑤G） | TCP keepalive。`Builder.keepAlive(KeepAlive)`、既定は`KeepAlive.DEFAULT`（30秒静かになったら15秒ごとに8回、答えなければ約2.5分で閉じる）。`Builder.disableKeepAlive()`で止められる。値は`jdk.net.ExtendedSocketOptions`が使える所で効き、ほかではOSの既定 |
| つなぎ直し（⑤） | `Builder.autoReconnect(boolean)`、既定true。`Builder.reconnectBackoff(initial, max)`（既定500ミリ秒、30秒）、`Builder.reconnectWindow(Duration)`（既定5分）。`MinecraftRemote.isConnected()`で今の状態を読める |
| 知らせ | `Builder.reconnectListener(ReconnectListener)`。`ReconnectEvent`は前と後の`HelloResult`、戻したbuild（`restoredBuild()`）、`serverChanged()`、`sessionChanged()`、`catalogHashChanged()`、`eventsMayHaveBeenLost()`（常にtrue）を持つ。つなぎ直した呼び出しのthreadで、その呼び出しを送る前に呼ぶ |
| 失敗の型 | `TransportException`の子に、`CompletionUnknownException`（送ったが完了不明、②③）と`NotSentException`（送っていない、⑤A）を足した。`NotSentException.reason()`は`CONNECTION_FAILED`、`INCOMPATIBLE_PROTOCOL`、`PAIRING_REQUIRED`、`HELLO_FAILED`、`DISCONNECTED`。`TransportException`は`final`を外した。今の`TransportException`をcatchしているapplicationは、そのまま動く |
| 次元の設定 | `setDimension(String)`（`build.setDimension`）を足した。⑤で戻すdimensionを、applicationが変えられるようにするため |

接続は、受信のthreadを1本持ち、応答待ちの表で応答を振り分ける形に変えた。送信とつなぎ直しは1つのlockでまとめるので、
`MinecraftRemote`は複数のthreadから呼べる。

### 既定の値と、そう選んだ理由

| 値 | 既定 | 理由 |
| --- | --- | --- |
| 応答の待ち | 15分（最後の受信から） | 検討メモの「最後の上限」の段で、普通の使い方では当たらない長さ。最後の受信から数えるので、FIFOの前に並んだ大きな建築の応答が届くたびに数え直す。TypeScriptの仮の値と同じにし、client間で比べやすくした。実測はしていない |
| keepalive | 30秒、15秒、8回（約2.5分） | 応答の待ち（15分）より十分短く、数十秒のWi-Fiの切断には耐える長さ。TypeScriptの30秒（OSの既定の間隔と回数）と始まりを揃えた。実測はしていない |
| つなぎ直しの間隔 | 500ミリ秒から2倍ずつ30秒まで。各回を最大半分までランダムに縮める | ⑤F。serverの再起動の後に多くのclientが同時につなぎ直しても、ばらける。TypeScriptの仮の値と同じ |
| つなぎ直しを試み続ける長さ | 5分 | 「長めに粘る」。Paperの再起動に足り、1回の呼び出しが無限に止まらない長さ。TypeScriptの仮の値と同じ |

### ⑤のA〜Gと`2026-11-12-05`

| 条件 | Javaでの満たし方 | 確かめたtest |
| --- | --- | --- |
| 次の呼び出しで、保存したtokenの新しいhelloから始め、最後に確かめたdimensionとoriginを戻す | `buildContext()`（`hello`、`build.setDimension`、`build.setOrigin`の成功だけで更新）を、新しいhelloの`build`にする。完了不明のsetterは戻さない | F1-OP-04、`reconnectsOnTheNextCallAfterThePeerClosedAndRestoresTheConfirmedBuild` |
| A 未送信と完了不明を分ける | つなぎ直せずに失敗した呼び出しは`NotSentException`、送った後に接続が閉じたものは`CompletionUnknownException` | F1-OP-01、02、04、05、`reconnectWaitsGrowWithJitterUntilTheWindowRunsOut` |
| B FASTとflushの境界 | FASTと`connection.flush`がJavaに無いので、入れるときに満たす | F1-OP-06はtodo |
| C 版と`catalogHash`の照合をやり直し、`server`と`session`の変化を知らせる | 新しいhelloでも版を判定し、合わなければ閉じて`NotSentException(INCOMPATIBLE_PROTOCOL)`。serverが`protocol_mismatch`を返したときも同じ。変化は`ReconnectEvent`で知らせる | F1-OP-07、`reportsWhatChangedWhenReconnecting` |
| D pairingは自動で進めない | つなぎ直しのhelloは、認証reasonでtokenを捨てた後のtokenなしhelloまで進めるが、`auth_required`ならpairingせず`NotSentException(PAIRING_REQUIRED)`。`pairingListener`があっても同じ | F1-OP-09 |
| E 複数のthreadでもつなぎ直しは1回 | 送信とつなぎ直しを1つのlockでまとめ、lockを得たthreadが閉じた接続を見つけたときだけつなぎ直す。後から来たthreadは、新しい接続へ送る | F1-OP-10、`threadsThatNoticeTheCloseTogetherReconnectOnce`（4 thread） |
| F 間隔をばらつかせながら延ばし、長めに粘る | 上の表の値。乱数は差し替えられる | `reconnectWaitsGrowWithJitterUntilTheWindowRunsOut`（乱数1.0と0.0で、待ちの列とwindowの終わりを確かめる） |
| G 何もしていない間の切断を「切れている」にする | TCP keepaliveで受信のreadが失敗すると、受信のthreadが接続を閉じ、`isConnected()`がfalseになる。次の呼び出しでつなぎ直す | F1-OP-12、`enablesTcpKeepAliveByDefaultAndCanTurnItOff` |
| ④ 自動で再送しない | 前の接続のrequestは送り直さない。`backpressure`も`RemoteCallException`で返す | F1-OP-02、03、04 |
| `2026-11-12-05` pairingの途中で切れたら、期限内なら新しい接続で同じ`pairing_id`のpollを続ける | 応答の前に切れたpollは`CompletionUnknownException`で終え、次のpollを新しい接続で送る。続けたpollの失敗では、二度目のpairingをしない。新しい接続を開けなければ、pairingを失敗にする | F1-AU-11、12、`continuesPairingOnANewConnectionAfterThePollResponseWasLost`、`doesNotPairAgainWhenTheContinuedPollFails` |
| ⑧ 次のpollの前にも期限を見る | 待つ間に`expires_in`を過ぎたら、次のpollを送らず`PairingExpiredException`。最初のpollは期限を見ずに送り、serverが期限切れを言えるようにする（F1-AU-02） | F1-OP-08、`checksThePairingDeadlineAgainBeforeTheNextPoll` |

### 落ちるのを見てから直したこと

直す前のclient（`main@223ef28`）で、fixtureを`335c604`へ替え、今のAPIで書けるtestを走らせた。

| test | 直す前 | 直した後 |
| --- | --- | --- |
| F1-AU-11 | c2のEOFの後、clientはc3を張らずに失敗し、peerはc3のacceptで止まった | PASS |
| `closesTheConnectionAndFailsEveryPendingRequestWhenTheResponseTimesOut`（③） | timeoutの後も接続がOPENのまま（expected CLOSED but was OPEN） | PASS |
| `reconnectsOnTheNextCallAfterThePeerClosedAndRestoresTheConfirmedBuild`（⑤） | つなぎ直さず、`TransportException`「Could not send world.getBlock」（Broken pipe） | PASS |
| `checksThePairingDeadlineAgainBeforeTheNextPoll`（⑧） | 期限を過ぎた後に3回目のpollを送り、peerが閉じて`TransportException` | PASS |

ほかの新しいv1 case（HE-11、12、CL-07、08、AU-12）は、直す前から通った。HE-11、12とAU-11の`connect`の読み方は、runnerを
直した（`client_close`はclientが自分で閉じたことの観測、pollで始まる接続はpairingの続き）。

v2の12 caseと、新しいAPIを使うunit testは、直す前のclientに無いAPIを使うので、直す前には走らせられない。代わりに、直した
後で、実装を一つずつ前の振る舞いへ戻し、意図した理由で落ちることを確かめた（下の表）。

| 戻した振る舞い | 落ちたtest | 落ち方 |
| --- | --- | --- |
| M1 つなぎ直しを既定で無効（`autoReconnect`の既定をfalse） | F1-OP-04、05、07、09、10、12、`reconnectsOnTheNextCall…`、`threadsThatNoticeTheCloseTogetherReconnectOnce`、`reconnectWaitsGrowWithJitter…`、`reportsWhatChangedWhenReconnecting` | `NotSentException`「automatic reconnecting is disabled」、OP-05はcauseが`connection_failed`でなく`disconnected`、ほかはpeerが新しい接続を待って止まる |
| M2 応答の待ちが尽きたら、そのrequestだけ外して接続を使い続ける（直す前の形） | `closesTheConnectionAndFailsEveryPendingRequestWhenTheResponseTimesOut` | expected CLOSED but was OPEN |
| M3 送信とつなぎ直しを1つのlockでまとめない | F1-OP-10、`threadsThatNoticeTheCloseTogetherReconnectOnce` | OP-10は新しい接続へ送る順が台本と違う、unit testは2本目の接続を開いて待ちが尽きる |
| M4 応答の前に切れたpollを、新しい接続で続けずにそのまま失敗にする | `continuesPairingOnANewConnectionAfterThePollResponseWasLost`、`doesNotPairAgainWhenTheContinuedPollFails` | `CompletionUnknownException`「Connection closed (peer EOF) while waiting for auth.pairPoll」 |
| M5 次のpollの前に期限を見ない | F1-OP-08、`checksThePairingDeadlineAgainBeforeTheNextPoll`、`reportsAClientJudgedPairingExpiryWithoutTheServerErrorShape` | OP-08は呼び出しが`pending`のまま、unit testはpollが続いて60秒で打ち切り |
| M6 つなぎ直しでもpairingする | F1-OP-09 | 呼び出しが`not_sent`／`pairing_required`で終わらず`pending`のまま |

v1のF1-AU-02は、この確認の間にM1、M2、M4で一緒に落ちた。調べると、戻した振る舞いと関係のない、runnerの競合だった。
直した後のclientでも12回中2回落ちていた。

- AU-02では、peerが`auth.pairBegin`の応答を書いた直後に、fixtureの時計を120秒進める。
- clientの受信のthreadがその応答を読む前に時計が進むと、runnerが仮想の時計で2秒にしていた応答の待ちが先に尽き、接続を
  閉じていた。
- v1の`advance_time`はpairingの期限のための時計で、v1には受信の待ちを見るcaseが無い。そこで、v1のrunnerの応答の待ちを1時間に
  した。直した後は25回続けて通った。

## 「すること」の6の答え

- ③の生存確認：設定できる。TCP keepaliveを既定で有効にし、`KeepAlive`（idle、interval、probes）で変えられる。値はLinuxなど
  `jdk.net.ExtendedSocketOptions`の`TCP_KEEPIDLE`、`TCP_KEEPINTERVAL`、`TCP_KEEPCOUNT`が使える所で効き、ほかでは
  SO_KEEPALIVEだけを有効にしてOSの既定に任せる。Javaはbrowserで動かない。wireのpingは無いので足していない
- 1つのclientのobjectを複数のthreadから使えるか：使える（この票で変えた）。requestは同じ接続のFIFOに並び、各threadは自分の
  応答を待つ。つなぎ直しは1回にまとまる。同じ接続を共有するので、あるthreadの大きな`setBlocks`の後ろで、別のthreadの
  `getBlock`が待つ（検討メモの「1」）

## 実機のpulse

1本通し、続けて実機でつなぎ直しを見た。どちらもPASS（2026-11-13 16:00〜16:03 JST）。

| 項目 | 内容 |
| --- | --- |
| 相手 | 担当の手元のPaperとMcRemote（helloのprotocol `23.2.1`、mc_version 1.21.11）。前回のpulseと同じ環境（Paper 1.21.11 build 132、McRemote `2321.1.0`） |
| Java | このbranch（元は`main@223ef28`）、Temurin 21.0.13 |
| 手順 | `mise exec -- ./gradlew --console=plain :examples:livePulse --args='--save reconnect'`。human ownerがMinecraftの中でpairingを承認した。PASSの後、human ownerがPaperを止めて起動し直し、その後でEnterを送った |
| 送ったもの | hello（`build`で作業場所の原点を要求）→ `world.setBlock`（金のブロック）→ `world.getBlock`。再起動の後に`world.getBlock`をもう1回 |
| 結果 | `world.getBlock`は`minecraft:gold_block`を返し、`pulse result: PASS` |
| つなぎ直し | 再起動の後、呼び出しの前の`isConnected()`はfalse（受信のthreadが切断を見つけていた）。次の`world.getBlock`が自分でつなぎ直し、`ReconnectEvent`は`server_changed=false`、`session_changed=false`、`catalog_hash_changed=false`、`events_may_have_been_lost=true`、戻したdimensionは`minecraft:overworld`。`world.getBlock`は同じ`minecraft:gold_block`を返し、`pulse reconnect result: PASS`、exit 0 |

- 認証の方法：examplesと同じ。session tokenをpairingで得て、`--save`でOSの資格情報storeへ保存する。2026-11-12に
  保存したsession tokenは期限を過ぎていて、最初にpairingに入った
- つなぎ直しの認証：保存したtokenを載せたhelloがそのまま通り、pairingには入らなかった（Paperの再起動でsession tokenは
  失われなかった）。`LivePulse`はwireを記録しないので、helloの回数やbackoffの待ちは観測していない
- 証跡の扱い：「live-auto相当」。scriptの`examples`の`LivePulse.java`（`reconnect`の選択を足した）とPASSのsummary。
  `14-evidence/`にrecordは作らない
- 片付け：`demo --args='--save 01-cleanup'`で、置いた金ブロックをairに戻した（exit 0）

## 違うと思ったcase、決めずに残したこと

契約と違うと思ったcaseは無い。

clientで新しく決めずに残したこと：

- 値はすべて仮で、実測していない（応答の待ち15分、keepalive約2.5分、つなぎ直しの間隔と5分）
- event cursorの失効：Javaのcursorは`EventPollResult`をapplicationが持つ。つなぎ直しを`ReconnectEvent`で知らせ、
  `pollEvents(0, ...)`から読み直すよう案内するが、前の接続のsnapshotを渡されたときにclientで拒むことはしていない
  （`EventPollResult`は公開のrecordで、接続のepochを持たせると形が変わる）。entity handleとcatalogのcacheは、まだJavaに無い
- pairingを続ける間に新しい接続を開けなかったときは、待たずにpairingを失敗にした。期限内に開け直しを繰り返すかは決めていない
- つなぎ直しのhelloが`auth_required`なら、保存したtokenを捨てる（⑦の流れと同じ）。applicationは`connect()`からpairingし直す
- 呼び出しごとの待ち時間：待ち時間は接続ごとに見る（最後の受信から）ので、`setResponseTimeout`で接続の長さを変える形にし、
  呼び出しごとの引数は足していない

## Protocol Inventoryの「共有fixtureの使い方」のJavaの行

前（knowledge `125812b`）：

| client | つないでいるもの | つないでいないもの |
| --- | --- | --- |
| Java | `chat-event-compat-v23.2.1.json`、`events-v23.json`、`sign-v23.json`、`spawn-v22.json`。F3でF1の6本（44 caseすべて）、`client-bootstrap-v1.schema.json`、`block-value-v22.json`（F1の`value_refs`の`/catalog/block`の範囲）を足した。2026-11-11に`ea7838a`の45 caseへ。2026-11-12に`36ebe9e`の48 caseすべてへ | `dimensions-v22.json`、`direction-lightning-v23.1.json`、`entity-particle-v23.2.1.json`（それぞれ対応する機能のsliceで）、`chat-event-compat-v23.2.json`、`entity-particle-v23.2.json`（履歴の版） |

後：

| client | つないでいるもの | つないでいないもの |
| --- | --- | --- |
| Java | `chat-event-compat-v23.2.1.json`、`events-v23.json`、`sign-v23.json`、`spawn-v22.json`。F3でF1の6本（44 caseすべて）、`client-bootstrap-v1.schema.json`、`block-value-v22.json`（F1の`value_refs`の`/catalog/block`の範囲）を足した。2026-11-11に`ea7838a`の45 caseへ。2026-11-12に`36ebe9e`の48 caseすべてへ。2026-11-13に`335c604`のv1 54 caseすべてと、F1 v2の`client-bootstrap-operations-v23.2.1.json`と`client-bootstrap-v2.schema.json`（12 caseのうち10 case。OP-06、OP-11はJavaに無い機能のためtodo）へ | 変わらない |

## 検証

- Temurin JDK 21.0.13と25.0.5で`./gradlew clean build`がPASS。各core 143件（skip 2：F1-OP-06、11のtodo）＋examples
  20件、failure／errorは0。API referenceの生成一致（`checkApiReference`）を含む。classfileはmajor 65
- F1 v1は54 caseすべてPASS、v2は12 caseのうち10 caseがPASS、2 caseがtodo
- flakyの確認：AU-02の競合を直した後、v1のrunnerを25回、coreのtest全体を10回続けて走らせ、すべて通った
- 直す前のclientで落ちることと、直した後に一つずつ戻して落ちることを、上の2つの表で確かめた
