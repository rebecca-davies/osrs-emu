# Host-patching RuneLite onto our local gateway — notes & results

Goal: make a fresh RuneLite injected-client JS5-download our cache from `127.0.0.1:43594` and
render the OSRS login screen (the first screenshottable milestone). Login-screen render needs only
JS5 + cache — no RSA/login patch.

**Status: SUCCESS — the OSRS login screen renders against our local gateway (2026-07-14, rev 239).**
The client connects to our gateway, handshakes at rev 239, downloads the whole login-screen cache
from us (35k+ groups across 17 archives, 0 misses), reaches `GameState.LOGIN_SCREEN` / "Cache is
ready", and renders the Old School title screen + world-select ("World 528") + RuneLite login form.

The final blocker was **not** the rev-235/239 cache mismatch that the earlier notes hypothesised.
Re-fetching `cache-data` at rev 239 (OpenRS2 id 2620) did **not** fix `error_game_js5crc` — the
client failed identically. The real cause was a **gateway bug in the JS5 prefetch response
encoding** (fix #6 below): we set the 0x80 "prefetch" bit on the compression byte, which this
injected client rejects, so it discarded and re-requested every prefetched group until its
CRC-failure counter tripped `js5crc`. Removing that one line reaches the login screen. See §3.

Screenshots of the actual states are in `tools/verify/verify-shots/` (that dir is gitignored):
- `rev239-LOGIN-SCREEN-2.png` — **the win**: the rendered OSRS login screen ("Welcome to RuneScape",
  Play Now, saved account, "World 528 – Click to switch") served entirely from our rev-239 gateway.
- `rev239-LOGIN-SCREEN.png` — same login screen with the first-run EULA accept dialog.
- `login-js5-connecting-to-update-server.png` — the client's native "Connecting to update server"
  JS5 loading bar rendering against OUR gateway.
- `login-js5crc-blocked-state.png` — the old stalled black-canvas state (pre-fix), after `error_game_js5crc`.

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
| 6 | **`js5crc` (the real final blocker).** The client fetches archive indexes (**urgent**, opcode 1) fine but re-requests every **prefetched** group (opcode 0) ~5× then gives up with `js5crc`. Isolation: every urgent group succeeded (incl. a 309 KB multi-block group); every prefetch group (all 1014 archive-4 requests) was retried. The only encoder difference was `stream[3] \|= 0x80` — setting the "prefetch" bit on the group's **compression byte**. This client never masks that bit off, so it reads an invalid compression type, discards the group, and re-requests it. | `Js5ResponseEncoder`: **delete the `if (prefetch) stream[3] \|= 0x80` line.** Prefetch and urgent responses are now byte-identical; the request opcode (0 vs 1) is the only prefetch signal the client uses. One-line fix; it is what actually reaches the login screen. |

After fixes 1–5 the client connects, handshakes (rev 239), sets XOR keys, and downloads real cache
groups. Verified with a from-scratch Python JS5 client (`scratchpad/js5probe*.py`) that the gateway's
output is **byte-perfect** for plaintext and XOR-encrypted responses, single- and multi-block, up to
the largest group in the cache (`2/10.dat`, 644 KB / ~1260 blocks) — every CRC matches the index.

All gateway/protocol/crypto unit tests pass, including new `Js5ResponseEncoderTest` cases for
version-trailer stripping and XOR round-trip.

---

## 3. The real `error_game_js5crc` root cause: the prefetch 0x80 bit (RESOLVED)

`js5crc` fires when the client's JS5 CRC-failure counter (`qy.ak` in the deobfuscated client) crosses
its threshold: a downloaded group is discarded (bad/undecodable) and re-requested too many times.
The earlier notes blamed a rev-235/239 cache mismatch. That was **wrong**. `cache-data` was re-fetched
at **rev 239** (OpenRS2 id 2620, 116,703 files) and the client failed **identically** — same
`error_game_js5crc`, same stall at internal loading state `1000` (black canvas).

The rev-239 diagnosis (all measured, not assumed):
- **Cache integrity (rev 239):** full disk audit — every archive index vs the master `255/255`, and
  every group vs its archive index (**116,679 groups**) — reports **0 bad, 0 missing**. 100%
  self-consistent. (The master index `255/255` is a flat table of `N × [crc:u32][version:u32]`, one
  entry per archive index, *not* a Js5Index.)
- **Gateway serving (rev 239):** a from-scratch JS5 client audited **every one of the 116,679 groups
  over the socket**, plaintext **and** XOR (key 0x5a), prefetch and urgent, single- and multi-block —
  **0 corrupt**. Every CRC matches the index.
- **Requests (rev 239):** temporary `store.read` logging in `Js5Handler` showed the client made
  **0 missing requests** and **0 unknown opcodes**; XOR keys arrive via control opcode 4 as expected.
- **Client-stored bytes:** parsing the client's own `main_file_cache.dat2` showed it received our
  served container bytes **byte-for-byte**, then appends its own 4-byte version trailer on disk
  (a storage artifact, not a wire desync). Confirms fix #4 (trailer stripping) is authentic.

**The decisive isolation:** in the `store.read` request log, every **urgent** group request
(opcode 1 — the archive indexes and a scatter of groups in archives 8/10/13/17, including a 309 KB
multi-block group) succeeded on the first try, while **every prefetch** group request (opcode 0 —
all 1014 archive-4 requests) was re-issued ~5× and never accepted, ending in `js5crc`. The *only*
difference between how the encoder built a prefetch vs an urgent response was one line:
`if (prefetch) stream[3] |= 0x80` — setting the "prefetch" bit on the group's compression byte. This
rev-239 injected client does not mask that bit off; it reads e.g. compression `0x82`, treats the
group as undecodable, discards it, re-requests, and trips the counter.

**Fix (#6): delete that line** so prefetch and urgent responses are byte-identical. After the fix the
client fetched **35,494 groups across 17 archives (0 misses, 0 retries)**, logged
`GameState.LOGIN_SCREEN` + "Cache is ready", and **rendered the login screen** (see the win
screenshot). Everything in connect → handshake → JS5 → cache → login-screen render is now proven
working end-to-end at rev 239.

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
Expected today (post fix #6): the client renders its "Connecting to update server" JS5 bar against
our gateway, downloads the full login-screen cache from us, then renders the OSRS **login screen**
(Old School title + world-select + RuneLite login form). No `error_game_js5crc`. Clear any stale
`~/.runelite/jagexcache` first so the download comes cleanly from us.

---

## 5. RSA login-key patch (Task 5) — so OUR server can decrypt the login block

Login-screen rendering (§1-§4 above) needs no RSA patch — the login block is only sent once the
player clicks Play Now. To have the client encrypt that block with a modulus **we** hold the
private key for, `tools/client-patch` generates our server keypair and byte-patches a copy of the
injected-client jar.

### What `tools:client-patch` does

```
./gradlew :tools:client-patch:run
```

1. Generates a fresh 1024-bit RSA keypair (`emu.crypto.Rsa.generateKeyPair(1024)`).
2. Persists it to `server-rsa.properties` at the repo root (hex `modulus` /
   `publicExponent` / `privateExponent`) — **gitignored**, read by the gateway (Task 6) to decrypt
   real login blocks. Never commit this file.
3. Prints the public modulus (256 hex chars) so a human can eyeball it.
4. Byte-patches a **copy** of the cached injected-client jar
   (`~/.gradle/caches/modules-2/files-2.1/net.runelite/injected-client/1.12.33-SNAPSHOT/…/injected-client-1.12.33-SNAPSHOT.jar`):
   it finds the single `.class` entry containing the exact Jagex modulus literal (256 hex chars,
   class `bg`, field `bg.af` — see
   `docs/superpowers/research/2026-07-14-rev239-login-facts.md` §3) and overwrites those bytes
   in-place with our own same-length modulus hex, leaving every other byte/offset in the jar
   untouched. The **original cached jar is never modified** — only a new file is written to
   `client/patches/injected-client-patched.jar` (gitignored, since all of `client/` is ignored).

Run `./gradlew :tools:client-patch:verifyRoundTrip` to prove the persisted keypair actually
round-trips: it encrypts a login-like plaintext (magic byte `1` + payload) with the public
modulus/exponent and decrypts it with the private exponent via `emu.crypto.Rsa.decrypt`, asserting
magic byte `1` comes back. This is the same math path the gateway will run against a real client
ciphertext in Task 6.

### How the patched jar gets loaded — classpath override, no cache clobbering

The injected-client classes are a `runtimeOnly` Gradle dependency that RuneLite's `:client:shadowJar`
task **merges directly into** the shaded fat jar at build time (see §1's build step) — they are not
loaded from a separate jar file at runtime. So dropping our patched jar into the `~/.gradle` cache
folder would do nothing (the shaded jar was already built from the old copy), and overwriting the
shaded jar's own class file would mean unzipping/rezipping a much bigger, frequently-rebuilt
artifact.

Instead, `run-local-client.sh` launches with **`java -cp <patched-jar>:<shaded-jar> net.runelite.client.RuneLite ...`**
instead of `java -jar <shaded-jar>`. Standard JVM classpath resolution loads a class from the
*first* `-cp` entry that contains it; since `client/patches/injected-client-patched.jar` (which
contains every original class, including our patched `bg.class`) is listed first, its `bg.class`
wins over the shaded jar's bundled (unpatched) copy for the entire run. Note `-jar` and `-cp` are
mutually exclusive (`-jar` ignores the classpath entirely per the `java` launcher docs), so this
requires invoking the explicit RuneLite main class (`net.runelite.client.RuneLite`) instead of
`-jar`.

This is fully restorable / non-destructive: nothing under `~/.gradle` or the shaded jar is ever
written to. If `client/patches/injected-client-patched.jar` is missing, `run-local-client.sh` falls
back to the original `-jar` launch (Jagex's login RSA key, fine for JS5/login-screen-only runs).

### Repro (adds to §4)

```
./gradlew :tools:client-patch:run          # generates server-rsa.properties + the patched jar
./gradlew :tools:client-patch:verifyRoundTrip   # proves the keypair round-trips

client/patches/run-local-client.sh \
  client/runelite/runelite-client/build/libs/client-1.12.33-SNAPSHOT-shaded.jar
# -> picks up client/patches/injected-client-patched.jar automatically if present
```
