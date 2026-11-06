# 固定したProtocol共有fixture

通常のbuild／CIはこのdirectoryのvendor copyを読み、networkから取得しません。
2026-11-04に取得元をtoolingへ切り替え、2026-11-06にMITのLICENSEを含むcommitへ固定し直しました。

- repository: `Naohiro2g/minecraft-remote-tooling`
- source commit: `21a2a9a0aed8b4d9c715da46616aad86f1174b4c`
- source directory: `packages/protocol/test/fixtures/`
- [固定したownerの案内](https://github.com/Naohiro2g/minecraft-remote-tooling/blob/21a2a9a0aed8b4d9c715da46616aad86f1174b4c/packages/protocol/README.md)
- 初回stableの比較用参照（履歴）: 公開tag `v2321.0.0` → `6447f6e8cbfbeeb9d229990b5c4f6e82a5875666`。
  現在のfixture取得元は上記のLICENSE追加後のcommitです

| file | bytes | SHA-256 | Javaでの消費 |
| --- | --- | --- | --- |
| `events-v23.json` | 2,112 | `31760d267f3c2641042fbe8595fda9c259134a1c05423271a99cb74da1efa9aa` | `Protocol23CapabilitiesTest`、examples |
| `sign-v23.json` | 5,043 | `7ffb63c264602cba56117eefff1f9604b955df04c5cc655e877772b8ff7cd30e` | 同上 |
| `spawn-v22.json` | 682 | `1120e6c8d41b05b65c916fa96f496b02884123ec7ef59b0a226eea48bebf3abd` | `Protocol23CapabilitiesTest` |
| `chat-event-compat-v23.2.1.json` | 32,384 | `eead244a7340c315cb63e1e77ede0fe9cf48162c06fedbeef36d941950d5000d` | `SharedCompatibilityFixtureTest`：chat 7、event 22、stateful 4 case |

2026-11-06に4本すべてを新取得元から取り直してlocal copyとbyte-for-byteで比較し、byte数とSHA-256も一致しました。
fixture本体は変更していません。`Protocol23CapabilitiesTest`も引き続き同じdigestを検証します。
新旧toolingのfixture 10本すべてについて、GitHub APIのGit blob SHAとbyte数が不変であることも確認しました。
Javaが使わない6本の理由・予定は[E1確認資料](../../../../docs/polyglot-e1_ja.md#javaが使っていない共有fixture)にあります。

既存3本は移管前のScratch owner `df9264ec355dd722a848df46e96d4b0fc9340ca2` の
`mc-remote/protocol/test/fixtures/`ともbyte一致しました。b6比較基線は取得履歴として保持します。
protocol 22のparticle shapeもprotocol 23へ引き継がれており、履歴のfile名を改名しません。

fixture内の`knowledge_contract`は成立時点の参照です。今回の着地確認で読んだknowledgeは
`8c81c0f115127189de117c3848d1e254f79571c9`で、現行contractの正本はknowledgeの`10-protocol`です。

## LICENSE

同じ取得commitの`packages/protocol/test/fixtures/LICENSE`を、このdirectoryの[LICENSE](LICENSE)へ
byte-for-byteでcopyしました。

- license: MIT（fixture directoryの範囲。toolingの他の範囲へ拡張しません）
- bytes: `1,070`
- SHA-256: `aa329a0bafc839b95124306141db13a381de45818bf629e983c44ea55ab5a7fb`

これにより、[既決2026-10-23-01](https://github.com/Naohiro2g/mc-remote-knowledge/blob/8c81c0f115127189de117c3848d1e254f79571c9/00-hub/DECISIONS_ja.md)③の
upstream LICENSE未反映という申し送りは解決済みです。
