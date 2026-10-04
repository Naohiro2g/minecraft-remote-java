# 固定したMcRemote owner fixture

このdirectoryのfileは、b6に統合されたScratch owner fixtureからbyte-for-byteでvendorしています。

- repository: `Naohiro2g/scratch-editor`
- commit: `df9264ec355dd722a848df46e96d4b0fc9340ca2`
- source directory: `mc-remote/protocol/test/fixtures/`

`Protocol23CapabilitiesTest`はfixtureの値をJava wire testで使い、local copyが黙って変わらないよう次のSHA-256も
固定します。

- `events-v23.json`: `31760d267f3c2641042fbe8595fda9c259134a1c05423271a99cb74da1efa9aa`
- `sign-v23.json`: `7ffb63c264602cba56117eefff1f9604b955df04c5cc655e877772b8ff7cd30e`
- `spawn-v22.json`: `1120e6c8d41b05b65c916fa96f496b02884123ec7ef59b0a226eea48bebf3abd`

protocol 22のparticle shapeはprotocol 23へ変更なく引き継がれています。これらはtest入力でありProtocol SSOTの
代替ではありません。Protocolの意味は`mc-remote-knowledge`を正本とします。
