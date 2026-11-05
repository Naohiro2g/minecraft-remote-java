# 固定したProtocol共有fixture

通常のbuild／CIはこのdirectoryのvendor copyを読み、networkから取得しません。
取得元は初回stableのtoolingへ切り替えました（2026-11-04）。

- repository: `Naohiro2g/minecraft-remote-tooling`
- public tag: `v2321.0.0`
- source commit: `6447f6e8cbfbeeb9d229990b5c4f6e82a5875666`
- source directory: `packages/protocol/test/fixtures/`
- [固定したownerの案内](https://github.com/Naohiro2g/minecraft-remote-tooling/blob/6447f6e8cbfbeeb9d229990b5c4f6e82a5875666/packages/protocol/README.md)

| file | SHA-256 | Javaでの消費 |
| --- | --- | --- |
| `events-v23.json` | `31760d267f3c2641042fbe8595fda9c259134a1c05423271a99cb74da1efa9aa` | `Protocol23CapabilitiesTest`、examples |
| `sign-v23.json` | `7ffb63c264602cba56117eefff1f9604b955df04c5cc655e877772b8ff7cd30e` | 同上 |
| `spawn-v22.json` | `1120e6c8d41b05b65c916fa96f496b02884123ec7ef59b0a226eea48bebf3abd` | `Protocol23CapabilitiesTest` |
| `chat-event-compat-v23.2.1.json` | `eead244a7340c315cb63e1e77ede0fe9cf48162c06fedbeef36d941950d5000d` | `SharedCompatibilityFixtureTest`：chat 7、event 22、stateful 4 case |

全fileは固定commitからbyte-for-byteで取得し、`Protocol23CapabilitiesTest`がdigestを検証します。
既存3本は移管前のScratch owner `df9264ec355dd722a848df46e96d4b0fc9340ca2` の
`mc-remote/protocol/test/fixtures/`ともbyte一致しました。b6比較基線は取得履歴として保持します。
protocol 22のparticle shapeもprotocol 23へ引き継がれており、履歴のfile名を改名しません。

fixture内の`knowledge_contract`は成立時点の参照です。今回読んだknowledgeは
`6d408c789aa72cffa2c547fe5ae97c3aa56e6d93`で、現行contractの正本はknowledgeの`10-protocol`です。

fixtureのMIT化はknowledgeの[決定2026-10-23-01](https://github.com/Naohiro2g/mc-remote-knowledge/blob/6d408c789aa72cffa2c547fe5ae97c3aa56e6d93/00-hub/DECISIONS_ja.md)③によります。
ただし取得commitではdirectoryの`LICENSE`が未配置（GitHub API 404）で、protocol packageの表記は
`AGPL-3.0-only`のままです。この未反映をknowledgeへの搬送事項に含め、upstreamのlicense整備済みとは扱いません。
