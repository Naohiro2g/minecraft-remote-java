# 固定したProtocol共有fixture

通常のbuild／CIはこのdirectoryのvendor copyを読み、networkから取得しません。
2026-11-04に取得元をtoolingへ切り替え、2026-11-06にMITのLICENSEを含むcommitへ固定し直しました。
2026-11-10に、F1の6本とそのschema、F1が参照する`block-value-v22.json`を加え、directory全体の取得元を
F1をmergeしたcommitへ固定し直しました。
2026-11-11に、F1の追補（tooling PR 5）をmergeしたcommitへ固定し直しました。
2026-11-12に、F1をE2の決定に合わせたcommit（tooling PR 7）へ固定し直しました。

- repository: `Naohiro2g/minecraft-remote-tooling`
- source commit: `36ebe9e63e4f0afcf949eae998832f68209b8119`
- source directory: `packages/protocol/test/fixtures/`
- [固定したownerの案内](https://github.com/Naohiro2g/minecraft-remote-tooling/blob/36ebe9e63e4f0afcf949eae998832f68209b8119/packages/protocol/README.md)
- 前の取得元（履歴）: `ea7838a78bf78947c7dde5876f6cc93b43f7e3cc`。`36ebe9e`で変わったcopyは
  `client-bootstrap-close-v23.2.1.json`、`client-bootstrap-pairing-v23.2.1.json`、`client-bootstrap-v1.schema.json`の3本です。
  ほかの9本とLICENSEはblobが同じです（2026-11-12にGitHubのtreeと比較）
- その前の取得元（履歴）: `8264c3881bb4128c14cda6d122641ebcfa67d9c5`。`ea7838a`で変わったcopyは
  `client-bootstrap-hello-v23.2.1.json`と`client-bootstrap-v1.schema.json`の2本です。ほかの10本とLICENSEは
  blobが同じです（2026-11-11にGitHubのtreeと比較）
- さらに前の取得元（履歴）: `21a2a9a0aed8b4d9c715da46616aad86f1174b4c`。下の表の既存4本とLICENSEは、
  `8264c38`でもbytesが同じです（2026-11-10にbyte-for-byteで比較）
- 初回stableの比較用参照（履歴）: 公開tag `v2321.0.0` → `6447f6e8cbfbeeb9d229990b5c4f6e82a5875666`

| file | bytes | SHA-256 | Javaでの消費 |
| --- | --- | --- | --- |
| `events-v23.json` | 2,112 | `31760d267f3c2641042fbe8595fda9c259134a1c05423271a99cb74da1efa9aa` | `Protocol23CapabilitiesTest`、examples |
| `sign-v23.json` | 5,043 | `7ffb63c264602cba56117eefff1f9604b955df04c5cc655e877772b8ff7cd30e` | 同上 |
| `spawn-v22.json` | 682 | `1120e6c8d41b05b65c916fa96f496b02884123ec7ef59b0a226eea48bebf3abd` | `Protocol23CapabilitiesTest` |
| `chat-event-compat-v23.2.1.json` | 32,384 | `eead244a7340c315cb63e1e77ede0fe9cf48162c06fedbeef36d941950d5000d` | `SharedCompatibilityFixtureTest`：chat 7、event 22、stateful 4 case |
| `client-bootstrap-framing-v23.2.1.json` | 15,131 | `be063b519b5d653a4bb4ec3da6f67e5d481a28dcf5913961c848b2b909d4b26d` | `ClientBootstrapFixtureTest`：7 case |
| `client-bootstrap-hello-v23.2.1.json` | 20,913 | `78297f05eb4352b0dabffab3242e933c1e68cb192bbb6d242019c7956c809d57` | 同上：10 case |
| `client-bootstrap-pairing-v23.2.1.json` | 35,577 | `5864c23ef3f31af6134d36770cab80b569b708b35fd3fdbed69fb6db195e3aeb` | 同上：10 case |
| `client-bootstrap-blocks-v23.2.1.json` | 45,945 | `664babdf3131a74e4aaf6d9e5dfb5e4cfc6e197df0a0d48142688820f1be813d` | 同上：12 case |
| `client-bootstrap-errors-v23.2.1.json` | 9,179 | `c458d3ffbae846cfb33c032eba14d8f0005c6a1fbe871290b53fcfec8ec41aaa` | 同上：3 case |
| `client-bootstrap-close-v23.2.1.json` | 14,884 | `a030cc72aa0e4f41575cb15610b1e06e941d3eab07157ce47f0eafe744475154` | 同上：6 case |
| `client-bootstrap-v1.schema.json` | 10,916 | `8c3e4ff6e7b5b0cf438d358578096579dafab699a40df9cd56de7b9513837413` | 同上：F1の6本の形を、このschemaの定数・列挙から確かめる |
| `block-value-v22.json` | 3,217 | `e8e108d59c40751d67a432543d75cb4bf9146ef9781721012085c80545b854f3` | 同上：blocksの`value_refs`（`/catalog/block`）を解決し、期待値の`result`がcatalogの範囲にあることを確かめる |

各testは、使うfileのSHA-256を検証します。fixture本体は変更していません。
F1の6本の読み方、Java側で直した振る舞い、決めずに残したことは[F3の記録](../../../../docs/polyglot-f3_ja.md)にあります。
E2の決定に合わせた固定し直しと、決定との違いは[F1とE2の記録](../../../../docs/polyglot-f1-e2_ja.md)にあります。
Javaが使わない共有fixtureの理由・予定は[E1確認資料](../../../../docs/polyglot-e1_ja.md#javaが使っていない共有fixture)にあります
（`block-value-v22.json`は、上のとおりF1の参照の範囲で使うようになりました）。

既存3本は移管前のScratch owner `df9264ec355dd722a848df46e96d4b0fc9340ca2` の
`mc-remote/protocol/test/fixtures/`ともbyte一致しました。b6比較基線は取得履歴として保持します。
protocol 22のparticle shapeもprotocol 23へ引き継がれており、履歴のfile名を改名しません。

fixture内の`knowledge_contract`は成立時点の参照です。現行contractの正本はknowledgeの`10-protocol`です。

## LICENSE

同じ取得commitの`packages/protocol/test/fixtures/LICENSE`を、このdirectoryの[LICENSE](LICENSE)へ
byte-for-byteでcopyしました。

- license: MIT（fixture directoryの範囲。toolingの他の範囲へ拡張しません）
- bytes: `1,070`
- SHA-256: `aa329a0bafc839b95124306141db13a381de45818bf629e983c44ea55ab5a7fb`

これにより、[既決2026-10-23-01](https://github.com/Naohiro2g/mc-remote-knowledge/blob/8c81c0f115127189de117c3848d1e254f79571c9/00-hub/DECISIONS_ja.md)③の
upstream LICENSE未反映という申し送りは解決済みです。
