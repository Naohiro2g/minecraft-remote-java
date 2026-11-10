# 多言語化 F3：JavaのCIでF1のfixtureを通す

knowledgeの`00-hub/polyglot-f3-instructions_ja.md`の「共通」と「Java」への返却の資料です。
F3の合格は、JavaのCIでF1の全caseがPASSし、実McRemoteを相手にしたpulseが1本通ることです。

## 参照identity

| 対象 | identity |
| --- | --- |
| knowledge | `5e00768911feb7be1e54ae3b7e9c2d776b88927a`（F3の依頼が入ったmain） |
| fixtureの取得元 | minecraft-remote-tooling `8264c3881bb4128c14cda6d122641ebcfa67d9c5`の`packages/protocol/test/fixtures/` |
| Java | このbranchの元は`main@f5ffc0670f2b1d94e8de0faf1303f91efa5466d5` |

toolingは`protocol_mismatch`のcaseを`2026-11-10-01`に合わせた。そのmerge commitがcoordinatorから届いたら、取得元を固定し直す。
それまでは依頼のとおり`8264c38`に固定する。

## fixtureの取得元

| | 前 | 後 |
| --- | --- | --- |
| commit | `21a2a9a0aed8b4d9c715da46616aad86f1174b4c` | `8264c3881bb4128c14cda6d122641ebcfa67d9c5` |
| 持ち方 | copy 4本＋`LICENSE` | copy 12本＋`LICENSE`（F1の6本、schema、`block-value-v22.json`を追加） |

既存の4本と`LICENSE`は、`8264c38`でもbytesが同じ。追加した8本のbytesとSHA-256は
[fixture一覧](../src/test/resources/protocol-fixtures/README.md)にある。`ClientBootstrapFixtureTest`は、
全fileのSHA-256と`LICENSE`のbytes（1,070）を検証する。

## fileごとのcase数とPASS数

`ClientBootstrapFixtureTest`は、F1の各caseを1つのJUnit testとして走らせる。skipは無い。

| file | case | PASS |
| --- | ---: | ---: |
| `client-bootstrap-framing-v23.2.1.json` | 7 | 7 |
| `client-bootstrap-hello-v23.2.1.json` | 9 | 9 |
| `client-bootstrap-pairing-v23.2.1.json` | 7 | 7 |
| `client-bootstrap-blocks-v23.2.1.json` | 12 | 12 |
| `client-bootstrap-errors-v23.2.1.json` | 3 | 3 |
| `client-bootstrap-close-v23.2.1.json` | 6 | 6 |
| 計 | 44 | 44 |

同じtest classは、ほかに次の3つを確かめる。

- 固定したbytes：上のとおり
- schemaの形：`client-bootstrap-v1.schema.json`の定数（`schema`、`protocol`、`knowledge_contract.commit`）、`suite`と`op`の列挙、
  caseの必須keyと許されるkey、`expected`のkey、caseのIDの形と、6本全体でIDが重ならないこと。JSON Schemaのlibraryは足していない
- `value_refs`：blocksの12 caseの`block-value-v22.json#/catalog/block`を解決し、期待値の`result`が、そのcatalogのblockで、
  full stateを持ち、値がcatalogの範囲にあることを確かめる

## Javaでの読み方

- framingの6 case（`peer_bytes`だけのcase）は、`FrameDecoder`へbyte chunkを順に入れ、`frames`と`tail_hex`を比べる
- そのほかのcaseは、`BootstrapFixturePeer`がloopbackのTCPでpeer側の手順を順に再生する。`client_send`は、clientが送った1行を
  JSONの値として比べる（memberの順は見ない。数は値で比べる）。`connect`、`client_send`、`client_close`でpeerが待つと、
  testがJavaの公開APIを呼ぶ。helloは`MinecraftRemote.Builder.connect()`、blockは`setBlock`／`getBlock`
- `advance_time`はfixtureの時計を進める。pairingの期限の計算は、package-privateの時計の差し替えでその時計を読む
- `connection`は、package-privateの`JsonRpcConnection.pollClosed`で、requestを送らずにpeerのEOFを読んで判定する。
  Javaの公開APIは同期的で、次のrequestまでEOFを読まないため
- 申告のprotocolは、package-privateの`Builder.protocol`で、caseのhelloの値（`23.2.1`、HE-09は`24.0.0`）を使う。
  Javaの既定の申告`23.0.0`は変えていない

Javaの型付きAPIでは作れないclient messageが、6 caseにある。これらは、clientの内部の
JSON-RPC接続（transport、idの採番、応答の相関、errorのdecodeはclientのもの）から、fixtureのmessageをそのまま送る。

| case | 型付きAPIで作れない理由 |
| --- | --- |
| F1-ER-01、F1-BL-09 | `state`が`null`。`BlockSpec`はstateのobjectを必須にしている |
| F1-BL-10 | `state`が無い。同上 |
| F1-BL-11 | block valueに余分なmember。同上 |
| F1-AU-03 | serverが発行していない`pairing_id`でpoll。`connect()`は発行されたIDでpollする |
| F1-AU-04 | 未知の`token_type`。`TokenType`は`SESSION`と`LONG_LIVED`だけ。helloの前にpairingを始める公開APIも無い |

BL-09〜11とER-01は、helloとその後の`getBlock`を公開APIで行い、壊れたmessageだけを内部の接続から送る。
AU-03とAU-04は、case全体を内部の接続から送る。

## 直したclientの振る舞い

どの修正も、直す前に、そのcaseが意図した理由で落ちることを見てから直した。

| 振る舞い | case | 前 | 後 |
| --- | --- | --- | --- |
| EOFの時の未完の末尾 | F1-FR-05、F1-CL-03 | `BufferedReader.readLine()`は、LFの無い末尾をEOFで1行として返す。LFの無い完全なJSONの後にEOFが来ると、成功した応答として受け取った（直す前にprobeで確認） | `FrameDecoder`はLFまでを1 frameとし、未完の末尾をEOFでも応答にしない。その時に待っていたidは`unanswered`になり、requestは今までどおり`TransportException`で失敗する |
| 行の区切り | F1-FR-01〜06 | `readLine()`はCRとCRLFでも行を区切った | LF（0x0A）だけで区切る。UTF-8は行がそろってからdecodeする |
| 応答待ちの表 | F1-FR-07 | `request()`が送信と待機を一体にしていて、2つ目のrequestを送れなかった。待っているid以外の応答は捨てた | `JsonRpcConnection`が、idごとの応答待ちの表で相関する。先に届いた別のidの応答は、そのidを待つまで保持する。公開APIは同期のまま |
| helloの`client` | F1-HE-01、HE-03〜09、F1-AU-01、AU-02、AU-05〜07、F1-BL-01〜12、F1-ER-01〜03、F1-CL-01〜03、CL-05、CL-06（32 case） | helloで`client`を送らなかった | `Builder.clientIdentity(...)`を呼んだときだけ、helloでも`client`を申告する。呼ばなければ今までどおり送らない（F1-HE-02の最小helloも通る）。pairingには今までどおり既定値を渡す |
| helloの`build` | F1-HE-03、F1-BL-01〜12、F1-ER-01〜03、F1-CL-01〜03、CL-05、CL-06（21 case） | helloで最初のbuild contextを要求できなかった | 公開APIに`Builder.buildContext(dimension, x, y, z)`を足した。dimensionは短いIDでもよく、正準の値はserverの応答から取る |
| client側の版の不一致 | F1-HE-04、HE-05 | `ProtocolException`（reasonなし） | 新しい`ProtocolMismatchException`。`reason()`は`protocol_mismatch`、`serverProtocol()`と`clientRequires()`を持つ。保存済みtokenは消さない |

serverが返す`protocol_mismatch`（F1-HE-09）は、前から`RemoteCallException`の`reason()`で判定していて、codeでは分けていない。
`data`の欄は`data()`にそのまま残る。McRemote `2321.1.0`の形（code `-32600`、`data`に`server`と`client_requires`）でも、
`-32000`でも、reasonで判定し、両方の欄を読め、tokenを消さないことを`MinecraftRemoteTest`で確かめた。

toolingの`ea7838a`（PR 5のmerge。HE-09の`data`に欄を足し、`-32600`のHE-10を足した版）の`hello`をlocalで一時的に差し替えると、
HE-09とHE-10もPASSした。固定し直す時には、`8264c38`から変わった`hello`のbytesとSHA-256、schemaの
`knowledge_contract.commit`が`const`から`enum`へ変わったことに合わせて、testを直す。

## 違うと思ったcaseと、決めずに残したこと

契約と違うと思ったcaseは無い。読み方の補足が1つある。

- F1-ER-01〜03の`matched_ids`は`[2]`で、helloの応答（id 1）を含まない。F1-FR-07はすべての相関を含む。
  Javaは、ER-01〜03ではhelloの後の観測として、hello以外の相関したidを比べる。toolingのREADMEに、`matched_ids`の
  範囲（caseの観測の対象のrequestだけか）が書いてあると、ほかの言語も迷わない

`unresolved`を持つcaseでは、`expected`に書いてあることだけを確かめ、次のことはclientで新しく決めていない。

- F1-CL-03、CL-05：EOFの時のrequestの例外の型は、前からの`TransportException`のまま。timeoutの時は、前と同じく
  そのidの応答を待たない（後で届いても捨てる）。自動の再送はしない
- F1-HE-09：messageの文言、codeの番号、切断の時機に依存しない
- F1-ER-01〜03：messageの文言とcodeの番号に依存しない。未知のreasonとdataはそのまま保つ
- F1-AU-07：承認を拒む専用の応答や`pair_denied`は扱わない

## 実McRemoteとのpulse

未実施。手元でPaperもMcRemoteも動いておらず、初回はMinecraftの中でのpairingの承認が要るため、人の操作の前で止めた。

- 認証の方法：examplesと同じ。session tokenをpairingで得て、`--save`ではOSの資格情報storeへ保存する
  （`--no-save`ではprocessのmemoryだけ）。pairingのコマンドはterminalに表示される
- 手順はPRの説明とknowledgeへの返却に書く。pulseは`./gradlew :examples:livePulse --args='--save'`で、
  hello（`build`で作業場所の原点を要求）→ `world.setBlock` → `world.getBlock`を1回ずつ送り、
  `pulse result: PASS`を表示する。置くのは作例`01`と同じ位置の金ブロック1個で、`demo --args=01-cleanup`でairに戻せる

## Protocol Inventoryの「共有fixtureの使い方」のJavaの行

前（knowledge `5e00768`）：

| client | つないでいるもの | つないでいないもの |
| --- | --- | --- |
| Java | `chat-event-compat-v23.2.1.json`、`events-v23.json`、`sign-v23.json`、`spawn-v22.json` | `block-value-v22.json`（F1で使う予定）、`dimensions-v22.json`、`direction-lightning-v23.1.json`、`entity-particle-v23.2.1.json`（それぞれ対応する機能のsliceで）、`chat-event-compat-v23.2.json`、`entity-particle-v23.2.json`（履歴の版） |

後（取得元はtooling `8264c38`）：

| client | つないでいるもの | つないでいないもの |
| --- | --- | --- |
| Java | `chat-event-compat-v23.2.1.json`、`events-v23.json`、`sign-v23.json`、`spawn-v22.json`、F1の6本（44 caseすべて）と`client-bootstrap-v1.schema.json`、`block-value-v22.json`（F1の`value_refs`の`/catalog/block`の範囲） | `dimensions-v22.json`、`direction-lightning-v23.1.json`、`entity-particle-v23.2.1.json`（それぞれ対応する機能のsliceで）、`chat-event-compat-v23.2.json`、`entity-particle-v23.2.json`（履歴の版） |

## 既定の申告のprotocolの案（Javaだけ）

変えていない。案と理由を返し、human ownerが決める。

| 案 | 内容 | 理由 | 気になる点 |
| --- | --- | --- | --- |
| A（推す） | `23.0.0`のまま。23.1や23.2のmethodを実装したsliceで、そのminorへ上げる | versioning「8.2」で申告は「clientが要求する版」。Javaが呼ぶmethodは、どれも`23.0.0`にある。23.1で増えたdirectionとlightning、23.2で増えたnearby、entity、sound、particle Stage 2は、Javaにまだ無い。`docs/compatibility_ja.md`も「実装済み機能に足りる`23.0.0`を維持」としている | `chat.post`の成功resultを`null`とした修正は`23.2.1`で版に刻まれ、Javaは`null`を検査する。`23.2.1`より前のbetaのserverでは、`postToChat`がProtocolExceptionになりうる。公開のserverは`23.2.1`以上なので、今の利用者には影響しない |
| B | `23.2.1`へ上げる | Pythonの申告（`23.2.1`）とF1のfixtureにそろう。初回stableの版と同じで、学習者への案内の版も一つになる | patchは見ないので、実際の要求は「23.2以上」になる。Javaが使わない機能のために、`23.0`〜`23.1`のserverを拒む |

## 検証

- Temurin JDK 21.0.13と25.0.5で`./gradlew clean build`がPASS。各core 104件＋examples 20件＝124件、failure／error／skipは0
- API referenceの生成一致（`checkApiReference`）を含む。classfileはmajor 65
