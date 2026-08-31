# Pinned McRemote owner fixtures

These files are vendored byte-for-byte from the b6-integrated Scratch owner fixture set:

- repository: `Naohiro2g/scratch-editor`
- commit: `df9264ec355dd722a848df46e96d4b0fc9340ca2`
- source directory: `mc-remote/protocol/test/fixtures/`

`Protocol23CapabilitiesTest` both consumes their values in Java wire tests and pins these SHA-256
digests so a local copy cannot silently drift:

- `events-v23.json`: `31760d267f3c2641042fbe8595fda9c259134a1c05423271a99cb74da1efa9aa`
- `sign-v23.json`: `7ffb63c264602cba56117eefff1f9604b955df04c5cc655e877772b8ff7cd30e`
- `spawn-v22.json`: `1120e6c8d41b05b65c916fa96f496b02884123ec7ef59b0a226eea48bebf3abd`

The protocol 22 particle shape is carried unchanged into protocol 23. These copies are test inputs,
not a replacement SSOT; protocol meaning remains in `mc-remote-knowledge`.
