# 多言語化：F1の固定し直しと、E2の決定との違い

knowledgeの`00-hub/polyglot-f1-e2-repin-instructions_ja.md`の「共通」と「Java」への返却の資料です。
E2の決定（`2026-11-11-01`①〜⑧、`2026-11-12-01`）に合わせたF1を、JavaのCIでつなぎ直し、Javaの今の振る舞いと決定との違いを
まとめます。

## 参照identity

| 対象 | identity |
| --- | --- |
| knowledge | 依頼が入ったcommitは`0b41bfd`。読んだのは、着手した時のmain `076a127a22eaf0188c1d13822bfd55bfeee9ec38`（`0b41bfd`を含む） |
| fixtureの取得元 | minecraft-remote-tooling `36ebe9e63e4f0afcf949eae998832f68209b8119`（PR 7のmerge）の`packages/protocol/test/fixtures/` |
| Java | このbranchの元は`main@dc8992324122611a579f66219a43c88d480fa844`（PR 3のmerge） |

toolingのmainは、着手した時に`36ebe9e`より先へ進んでいた。依頼のとおり`36ebe9e`に固定する。

## fixtureの取得元

| | 前 | 後 |
| --- | --- | --- |
| commit | `ea7838a78bf78947c7dde5876f6cc93b43f7e3cc` | `36ebe9e63e4f0afcf949eae998832f68209b8119` |
| 持ち方 | copy 12本＋`LICENSE` | 同じ12本＋`LICENSE`。変わった3本をcopyし直した |

`ea7838a`から`36ebe9e`で変わったcopyは次の3本で、ほかの9本と`LICENSE`は、GitHubのtreeでblobが同じ。依頼の票は
`131f315`からの差分を挙げているが、Javaの前の取得元`ea7838a`からの差分も、同じ3本だった。

| file | bytes | SHA-256 | 変わったこと |
| --- | ---: | --- | --- |
| `client-bootstrap-close-v23.2.1.json` | 14,884 | `a030cc72aa0e4f41575cb15610b1e06e941d3eab07157ce47f0eafe744475154` | F1-CL-03、CL-05に`local_failures`と`pending_ids` |
| `client-bootstrap-pairing-v23.2.1.json` | 35,577 | `5864c23ef3f31af6134d36770cab80b569b708b35fd3fdbed69fb6db195e3aeb` | F1-AU-07の前提、F1-AU-08〜10の追加、`pairing_attempts` |
| `client-bootstrap-v1.schema.json` | 10,916 | `8c3e4ff6e7b5b0cf438d358578096579dafab699a40df9cd56de7b9513837413` | `local_failures`と`pairing_attempts`、`knowledge_contract`の`commit`と`instructions`の許す値 |

## fileごとのcase数とPASS数

| file | case | PASS |
| --- | ---: | ---: |
| `client-bootstrap-framing-v23.2.1.json` | 7 | 7 |
| `client-bootstrap-hello-v23.2.1.json` | 10 | 10 |
| `client-bootstrap-pairing-v23.2.1.json` | 10 | 10 |
| `client-bootstrap-blocks-v23.2.1.json` | 12 | 12 |
| `client-bootstrap-errors-v23.2.1.json` | 3 | 3 |
| `client-bootstrap-close-v23.2.1.json` | 6 | 6 |
| 計 | 48 | 48 |

skipは無い。`ClientBootstrapFixtureTest`は、ほかに固定したbytes、schemaの形、`value_refs`を確かめる（[F3の記録](polyglot-f3_ja.md)）。

## 新しい期待値の読み方

- `local_failures`：列のidが、接続の`unanswered`と同じで、そのrequestの呼び出しが`TransportException`で失敗したこと。
  serverのerror（`RemoteCallException`）ではないこと
- `pairing_attempts`：その対話のすべての接続で送った`auth.pairBegin`の数
- `discard_token: true`：拒まれた最初のtokenが、storeに残っていないこと。F1-AU-08のように、後で新しいtokenを得たcaseでは、
  storeには`token`の値が入っている（toolingのREADMEのとおり。前のJavaは「storeが空」と読んでいた）
- `knowledge_contract`の`commit`と`instructions`：schemaの`const`または`enum`の値のどれかであること
- F1-AU-07は、tokenを捨てたところで終わる。Javaは決定⑦のとおり、続けてtokenなしのhelloへ進む。testのpeerは、scriptを
  終えたら待ち受けを閉じるので、そのhelloの接続は拒まれ、`connect()`は`TransportException`で失敗する。Javaはこのとき、
  前に受けたtokenの拒否（`token_invalid`の`RemoteCallException`）を抑制された例外（`getSuppressed()`）として付けるので、
  testはcaseの`reason`をそこから読む

## 決定との違い（②④⑦⑧と`2026-11-12-01`）

| 決定 | 前の振る舞い | 決定との違い | 直したか | 落ちたcase、前と後 |
| --- | --- | --- | --- | --- |
| ② 閉じたら応答待ちを「完了不明」に | 相手のEOFとsocketのerrorで、応答待ちを`unanswered`へ移し、`TransportException`で失敗させる。LFの無い末尾は応答にしない（F3で直した） | 無い。例外の型は言語ごとで、Javaは`TransportException`。reasonを作らない。timeoutの時は下の③ | 直していない | CL-03、CL-05は、`local_failures`が加わっても前からPASS |
| ④ 自動で再送しない | 再送しない。`backpressure`を含め、serverのerrorは`RemoteCallException`でapplicationへ返す。つなぎ直しも無い | 無い | 直していない | — |
| ⑦ 認証reasonの後はtokenなしのhelloへ | `token_*`でtokenを捨てると、すぐpairingへ進んだ。`pairingListener`が無ければ`PairingRequiredException(token_*)` | あった | 直した | F1-AU-08、AU-10：前はc2で`auth.pairBegin`を送り、tokenなしのhelloを待つpeerと食い違った。F1-AU-09：前は`PairingRequiredException(token_expired)`で、`pairing_required`がtrueになった。後は、tokenを捨ててtokenなしのhelloへ戻り、`auth_required`のときだけ一度pairingする。pairingの後のhelloが認証reasonで拒まれたら、二度目のpairingをせずに失敗を返す |
| ⑧ clientが判断したpairingの期限切れ | `expires_in`を過ぎると、`RemoteCallException(-32000, "pairing code expired", {reason: pair_expired})`を自分で作った | あった | 直した | F1に無いので、担当のunit test `reportsAClientJudgedPairingExpiryWithoutTheServerErrorShape`。前は`RemoteCallException`で落ちた。後は新しい`PairingExpiredException`（`reason()`は`pair_expired`、`expiresIn()`）で、codeとdataを持たない。serverが言った期限切れは、今までどおり`reason()`が`pair_expired`の`RemoteCallException`（F1-AU-02） |
| `2026-11-12-01` clientの側の不一致で閉じる | `ProtocolMismatchException`を投げる前に接続を閉じ、次のrequestを送らない | 無い | 直していない | 担当のunit test `closesWithoutAnotherRequestWhenHelloAdvertisesAnIncompatibleProtocol`を足した。足した時からPASS（peerは、helloの応答の後にclientが何も送らずに閉じることを確かめる） |

⑦の後のJavaの`connect()`：

1. 保存したtokenでhelloを送る
2. `token_expired`、`token_revoked`、`token_not_found`、`token_invalid`なら、tokenを捨て、新しい接続でtokenなしのhelloへ戻る
3. `auth_required`なら、`pairingListener`があるときだけ、専用の接続でpairingし、新しい接続でtoken付きのhelloを送る。
   無ければ`PairingRequiredException`（`reason()`は`auth_required`）
4. pairingは一回の`connect()`につき一度。その後のhelloが認証reasonで拒まれたら、tokenを捨てて`RemoteCallException`を返す
5. tokenの拒否の後の段で失敗したら、前の拒否を抑制された例外として付ける

認証errorの後は、serverが接続を閉じるかによらず、新しい接続でhelloを送る（F1-AU-08の前提：EOFはpeerのscenario）。

公開APIの変化：`PairingExpiredException`を足した。`PairingRequiredException.reason()`は`auth_required`だけになった
（前は捨てたtokenの認証reasonもありえた）。`pairingListener`を設定していなくても、tokenが拒まれたらtokenなしのhelloを試す
ので、認証を強制しないserverには、そのままつながる（F1-AU-09）。

## ③⑤：今の振る舞いと、APIの形の案

入れるのは次の票。値は実測で決める（決定③）ので、下の値はまだ決めていない。

### ③ 応答の待ち方と生存確認

今の振る舞い：

- `Builder.readTimeout(Duration)`、既定10秒。socketのread timeoutなので、応答を待つ間の「最後に受信してからの時間」に近い
  （何かbytesが届けば数え直す）。呼び出しごとには延ばせない
- 超えると、そのidだけを応答待ちから外して`TransportException`で失敗し、接続は開いたまま使い続ける。後から届いた応答は
  知らないidとして捨てる。決定③の「超えたら接続を回収する」と違い、却下案の「timeoutの後も接続を使い続ける」に当たる
- 生存確認は無い（TCPのkeepaliveも設定していない）。何もしていない間の切断は、次のrequestを送るまで分からない
- `Builder.connectTimeout(Duration)`、既定10秒

案：

- `Builder.responseTimeout(Duration)`を足し、`readTimeout`はその別名として残す。既定は長くし（値は実測）、応答待ちがある間、
  その接続で最後に受信してからの時間で見る。`MinecraftRemote.responseTimeout(Duration)`で、後の呼び出しの待ち時間を延ばせる
- 超えたら接続を閉じ、応答待ちを②と同じ「完了不明」で失敗させる。その接続は使わない
- 生存確認：TCPのkeepaliveを有効にし、OSが対応すれば`jdk.net.ExtendedSocketOptions`の`TCP_KEEPIDLE`／`TCP_KEEPINTERVAL`／
  `TCP_KEEPCOUNT`で間隔を決める。`Builder.keepAlive(Duration idle)`で変えられる。Wi-Fiの一時的な切断に耐える長さにする。
  wireにping methodは無いので、wireは足さない
- 失敗の型：`CompletionUnknownException extends TransportException`（送ったが、完了が分からない）。今の`TransportException`を
  catchしているapplicationは、そのまま動く

### ⑤ 自動のつなぎ直し

今の振る舞い：

- つなぎ直しは無い。接続が閉じた後の呼び出しは、送らずに`TransportException`（"Connection is closed"）で失敗する。
  applicationは`Builder.connect()`から作り直す
- 作り直したhelloの`build`は、`Builder.buildContext(...)`で決めた値。`setOrigin`で変えた原点は戻らない
- event cursorはapplicationが持つ（`pollEvents(previous)`）。entity handle、FAST、`connection.flush`は、Javaにまだ無い
- 公開methodは並行に使えない（classの注記）

案：

- `Builder.autoReconnect(boolean)`、既定true。接続が閉じた後の次の呼び出しで、新しい接続のhelloから始める。保存したtokenを
  使い、最後にserverが確かめたdimensionとorigin（helloの応答、`build.setOrigin`の応答）を`build`で戻す
- `Builder.reconnectListener(ReconnectListener)`：つなぎ直したら`reconnected(ReconnectEvent)`を呼ぶ。eventは、前と後の
  `HelloResult`、`server`と`session`が変わったか、その間のeventのlossがありうること、戻したbuild contextを持つ
- (A) つなぎ直せずに失敗した呼び出しは`NotSentException extends TransportException`（送っていない）。③の
  `CompletionUnknownException`と分ける
- (B) FASTと`connection.flush`を入れる時に、flushの結果が前の接続の設置まで保証するように見せない。今はJavaに無いので、
  入れる時の条件にする
- (C) つなぎ直したhelloでも版の互換判定をやり直し、合わなければ`ProtocolMismatchException`で閉じる（`2026-11-12-01`と同じ）。
  `catalogHash`が変わったら、保持しているcatalogを無効にして知らせる
- (D) つなぎ直しで`auth_required`になったら、`pairingListener`があってもpairingせず、`PairingRequiredException`で止めて知らせる
- (E) 公開methodは並行に使えないままにする。並行に使えるようにする時は、つなぎ直しを1回にまとめるlockを持つ
- (F) `Builder.reconnectBackoff(Duration initial, Duration max)`。間隔はばらつかせながら延ばす（値は実測）
- (G) ③の生存確認で切れたと分かったら、状態を「切れている」にし、次の呼び出しでつなぎ直す。`MinecraftRemote.isConnected()`で
  読める

## 違うと思ったcase、決めずに残したこと

契約と違うと思ったcaseは無い。読み方の補足が1つある。

- F1-AU-07は、tokenを捨てたところで終わるが、決定⑦のclientは、続けてtokenなしのhelloへ進む。caseの範囲（どこで観測を
  終えるか）がREADMEに書いてあると、ほかの言語も迷わない。Javaは、scriptの後の接続をpeerが拒むことで観測を終えている
  （上の「新しい期待値の読み方」）

clientで新しく決めていないこと：

- ③の値（応答のtimeout、生存確認の間隔）と、⑤のbackoffの値
- ②の例外の型の細分（「完了不明」と「未送信」）は、上の案に置き、今は`TransportException`のまま
- serverが認証errorの後に接続を閉じるか。Javaは閉じるかによらず、新しい接続でhelloを送る

## 実McRemoteとのpulse

⑦で認証の流れを変えたので、1本通した。PASS（2026-11-12 05:46 JST）。

| 項目 | 内容 |
| --- | --- |
| 相手 | 担当の手元のPaperとMcRemote（helloのprotocol `23.2.1`、mc_version 1.21.11）。前回のpulseと同じ環境（Paper 1.21.11 build 132、McRemote `2321.1.0`）で、今回は版をhelloの値でだけ確かめた |
| Java | このbranch（元は`main@dc89923`）、Temurin 21.0.13 |
| 手順 | `mise exec -- ./gradlew --console=plain :examples:livePulse --args='--save'`。human ownerがMinecraftの中でpairingを承認した |
| 送ったもの | hello（`build`で作業場所の原点を要求）→ `world.setBlock`（金のブロック）→ `world.getBlock`を1回ずつ |
| 結果 | `world.getBlock`は`minecraft:gold_block`を返し、`pulse result: PASS`、exit 0 |

- 認証の方法：examplesと同じ。session tokenをpairingで得て、`--save`でOSの資格情報storeへ保存する
- 認証の流れ：2026-11-10に保存したsession tokenは2時間の期限を過ぎていて、今回はpairingに入った。`LivePulse`はwireを
  記録しないので、最初のhelloがそのtokenを載せて認証reasonで拒まれ、tokenなしのhelloで`auth_required`になったこと（⑦の流れ）は、
  pairingに入ったことからの推定で、観測はしていない。⑦の流れそのものはF1-AU-08〜10で確かめている
- 証跡の扱い：「live-auto相当」。scriptの`examples`の`LivePulse.java`とPASSのsummary。`14-evidence/`にrecordは作らない
- 片付け：前回置いた金ブロックと同じ位置で、worldは変わっていない。`demo --args='--save 01-cleanup'`でairに戻せる

## Protocol Inventoryの「共有fixtureの使い方」のJavaの行

前（knowledge `076a127`）：

| client | つないでいるもの | つないでいないもの |
| --- | --- | --- |
| Java | `chat-event-compat-v23.2.1.json`、`events-v23.json`、`sign-v23.json`、`spawn-v22.json`。F3でF1の6本（44 caseすべて）、`client-bootstrap-v1.schema.json`、`block-value-v22.json`（F1の`value_refs`の`/catalog/block`の範囲）を足した。2026-11-11に`ea7838a`の45 caseへ | `dimensions-v22.json`、`direction-lightning-v23.1.json`、`entity-particle-v23.2.1.json`（それぞれ対応する機能のsliceで）、`chat-event-compat-v23.2.json`、`entity-particle-v23.2.json`（履歴の版） |

後：

| client | つないでいるもの | つないでいないもの |
| --- | --- | --- |
| Java | `chat-event-compat-v23.2.1.json`、`events-v23.json`、`sign-v23.json`、`spawn-v22.json`。F3でF1の6本、`client-bootstrap-v1.schema.json`、`block-value-v22.json`（F1の`value_refs`の`/catalog/block`の範囲）を足した。2026-11-12に`36ebe9e`の48 caseすべてへ | 変わらない |

## 検証

- Temurin JDK 21.0.13と25.0.5で`./gradlew clean build`がPASS。各core 111件＋examples 20件、failure／error／skipは0。
  API referenceの生成一致（`checkApiReference`）を含む。classfileはmajor 65
- F1は48 caseすべてPASS。⑦⑧の修正の前に、F1-AU-08〜10と⑧のunit testが、上の表の理由で落ちることを確かめた
