# Host-patching RuneLite onto our local gateway — notes & results

Goal: make a fresh RuneLite injected-client JS5-download our cache from `127.0.0.1:43594` and
render the OSRS login screen (the first screenshottable milestone). Login-screen render needs only
JS5 + cache — no RSA/login patch.

**Status: partial.** The client now connects to our gateway, completes the JS5 handshake, and
downloads real cache groups from us (1000+ groups, byte-for-byte correct). It does **not** yet reach
the rendered login screen — it stalls in the client's own JS5 loading state after reporting
`error_game_js5crc`. Root cause of the remaining block is a **cache revision mismatch** (our
`cache-data` is OSRS rev 235; the freshly-cloned RuneLite is rev **239**), documented below. Every
other layer is proven correct.

Screenshots of the actual states are in `tools/verify/verify-shots/` (that dir is gitignored):
- `login-js5-connecting-to-update-server.png` — the client's native "Connecting to update server"
  JS5 loading bar rendering against OUR gateway (only appears once the JS5 handshake succeeds and
  the cache download begins). This is the strongest visual proof the pipeline works.
- `login-js5crc-blocked-state.png` — the stalled black-canvas state after `error_game_js5crc`.

---

## 1. How to make RuneLite connect to 127.0.0.1 — no source patch needed

RuneLite's injected client derives its JS5 / game-server host from the **`codebase`** URL in the
`jav_config` it fetches at startup. The relevant chain:

- `net.runelite.client.RuneLite#main` accepts a `--jav_config <url>` argument
  (default `RuneLiteProperties.getJavConfig()` = `https://oldschool.config.runescape.com/jav_config.ws`).
- `ClientLoader.downloadConfig()` fetches that URL, parses `codebase`, `initial_jar`, `initial_class`
  and the `param=…` applet properties.
- `RSAppletStub.getCodeBase()` hands the codebase URL to the injected client, which uses its **host**
  for the JS5 update-server socket on port **43594**.
- `loadClient()` does `loadClass(config.initial_class)` — i.e. it instantiates the bundled
  `injected-client` classes (a Maven `runtimeOnly` dep, cached in `~/.gradle/…/injected-client`),
  it does **not** download a gamepack. So `codebase` is used purely as the connection host.

**Therefore the cleanest hook is a custom `jav_config` served over HTTP with
`codebase=http://127.0.0.1/`, passed via `--jav_config`. No RuneLite source edit is required.**
(`ClientConfigLoader` uses okhttp `HttpUrl`, which only accepts http/https — so the jav_config must
be served over HTTP, not `file://`.)

Artifacts (this directory, `client/patches/`):
- `jav_config.local.ws` — the real OSRS jav_config with `codebase` rewritten to `http://127.0.0.1/`.
- `run-local-client.sh <shaded.jar>` — serves that jav_config on `127.0.0.1:8080` and launches
  RuneLite with `--jav_config http://127.0.0.1:8080/jav_config.local.ws --debug`.

### Building the client
RuneLite is Gradle (not Maven). The runnable fat jar comes from a `shadowJar` task on the `:client`
project:
```
cd client/runelite && ./gradlew :client:shadowJar
# -> runelite-client/build/libs/client-1.12.33-SNAPSHOT-shaded.jar  (Main-Class net.runelite.client.RuneLite)
```
Builds offline against the cached `injected-client-1.12.33-SNAPSHOT` jar. Java 11.

---

## 2. Gateway fixes required to actually serve this client (tracked source, committed)

The client connected on the first try, but the gateway's JS5 implementation was incomplete for a
real client. Each fix below was verified end-to-end. Files are in the tracked emulator tree.

| # | Problem observed | Fix |
|---|---|---|
| 1 | Handshake rejected: `error_game_js5connect_outofdate`. Captured handshake shows the client sends **revision 239**, not the assumed 235. | `gateway/.../js5/Js5Handshake.kt`: expected revision `235 → 239` (+ a one-line handshake log). The fresh RuneLite tracks live OSRS and had bumped past 235. |
| 2 | `error_game_js5io` immediately. After the handshake the client sends JS5 **control opcodes** (first byte was `03` = logged-out). The pipeline had decoders only for group requests (op 0/1), so `ProtocolStage` treated op 3 as "unknown opcode" and dropped the socket. | New `Js5Control` + `Js5ControlDecoder`; bind opcodes **2,3,4,6,7** in `Main.kt`; `Js5Handler` consumes them (no response). |
| 3 | Still `js5io`; the master-index request `store.read(255,255)` returned **null** although `cache-data/cache/255/255.dat` exists. Gradle's `run` task uses the **subproject** dir as CWD, so `FlatFileStore(File("cache-data"))` resolved to `gateway/cache-data` (absent). | `gateway/build.gradle.kts`: `tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }`. |
| 4 | Still `js5io`. The stored `.dat` containers carry a trailing **2-byte version**; the JS5 client sizes each group from its `[compression][length]` header (`5|9 + compressedLength`) and reads exactly that, never the trailer. Sending it left 2 stray bytes that desynced the next group. | `Js5ResponseEncoder`: `servedBytes()` truncates each container to its header-declared length. Index groups (archive 255) have no trailer, so nothing is dropped there. |
| 5 | `js5io` → **`js5crc`** progress. Most connections send JS5 control **opcode 4** with a random **XOR key** (e.g. 0x7F, 0x99) and expect every response byte XORed with it; we were sending plaintext. | New `crypto/Js5XorCipher` (mutable key); `Js5Handler` sets the key from opcode 4; `Js5ResponseEncoder` XORs every outgoing byte via the per-connection cipher the pipeline already threads through `ProtocolStage`. Plaintext connections use key 0 (no-op). |

After fixes 1–5 the client connects, handshakes (rev 239), sets XOR keys, and downloads real cache
groups. Verified with a from-scratch Python JS5 client (`scratchpad/js5probe*.py`) that the gateway's
output is **byte-perfect** for plaintext and XOR-encrypted responses, single- and multi-block, up to
the largest group in the cache (`2/10.dat`, 644 KB / ~1260 blocks) — every CRC matches the index.

All gateway/protocol/crypto unit tests pass, including new `Js5ResponseEncoderTest` cases for
version-trailer stripping and XOR round-trip.

---

## 3. The remaining blocker: `error_game_js5crc` (rev-235 cache vs rev-239 client)

After fixes 1–5 the client downloads 400–1100+ groups (heavily archive 4) and then reports
`error_game_js5crc` and stalls at internal loading state `1000` (black canvas). `js5crc` fires when
the client's JS5 CRC-failure counter (`qy.ak` in the deobfuscated client) crosses its threshold: a
downloaded group's CRC-32 does not match the value in the archive index.

What was ruled out (all proven, not assumed):
- **Cache integrity:** a full audit of `cache-data` (every archive index vs the master index, and
  every group vs its archive index — ~25k+ groups) reports **0 bad, 0 missing**. The cache is 100%
  internally CRC-consistent.
- **Gateway output:** capturing the real client's traffic through a logging MITM proxy and parsing
  it exactly as the client does (deblock 0xFF markers, XOR-decrypt, read header-declared length) —
  every group on every connection (plaintext + encrypted) has the correct CRC and the streams
  consume end-to-end with no desync. **Zero corrupt groups served.**
- **Missing groups:** logging `store.read` misses shows the client never requests a group we lack.
- **Concurrency:** the gateway runs connection coroutines on the single `runBlocking` dispatcher;
  the encoder is stateless and the XOR cipher is per-connection, so there is no data race.
- **GPU rendering:** identical result under `--safe-mode` (GPU plugin disabled), so it is not a
  render issue — the client genuinely never leaves JS5 loading.

Most likely cause: the provided `cache-data` is **OSRS rev 235** content, but the freshly-cloned
RuneLite injected-client is **rev 239**. The client handshakes at 239 and validates our
(self-consistent) rev-235 CRCs successfully for the bulk of the download, but the rev-239 client's
scene/content expectations diverge from rev-235 data enough that its CRC-failure counter eventually
trips. A stale on-disk Jagex cache in `~/.runelite/jagexcache/oldschool/LIVE/` was also present and
was cleared for testing (then restored); clearing it did not resolve `js5crc`, which points at the
content-revision gap rather than local cache pollution.

Secondary observation (not fully isolated): the client cycles/aborts JS5 connections during the
heavy download (gateway sees `Broken pipe` / `Connection reset by peer` — always client-initiated).
If the client mishandles a group whose connection it drops mid-transfer, that could also feed the
CRC-failure counter. This may be aggravated by our single-threaded, file-backed gateway being slower
than Jagex under the client's aggressive pipelining. The `js5crc` was intermittent early on (one
22 s run showed zero errors, progressing) and became consistent as more of the (rev-235) cache was
fetched — consistent with the rev-content gap being the dominant factor.

**Most promising next step: re-fetch `cache-data` at rev 239 to match the client** (or pin the client
to a rev-235 build). Everything else in the connect → handshake → JS5 → cache pipeline is working and
proven.

---

## 4. Repro

```
# 1) gateway (serves rev-239 JS5 handshake + our cache from repo root)
cd /home/bec/code/osrsemu && ./gradlew :gateway:run          # "gateway listening on 43594"

# 2) build client (once)
cd client/runelite && ./gradlew :client:shadowJar

# 3) clear any stale Jagex cache so the client downloads cleanly from us
mv ~/.runelite/jagexcache ~/.runelite/jagexcache.bak 2>/dev/null || true

# 4) launch pointed at us
client/patches/run-local-client.sh \
  client/runelite/runelite-client/build/libs/client-1.12.33-SNAPSHOT-shaded.jar

# 5) screenshot (login state)
tools/verify/screenshot-login.sh -w 60      # captures the current display
```
Expected today: the client renders its native "Connecting to update server" JS5 bar against our
gateway, downloads real groups, then stalls with `error_game_js5crc` (see §3).
