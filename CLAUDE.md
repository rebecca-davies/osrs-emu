# osrsemu — engineering rules (every agent MUST follow)

An OSRS server emulator that RuneLite connects to and logs into. These rules are binding for
every task and every subagent. When dispatching an agent, point it here. Violations should fail
review.

## Architecture — composable, never monolithic

1. **No god-objects.** A connection "session" is a *composition* — a decoder + a pure handler +
   an encoder driven by `net-core`'s `ProtocolStage`. Never a `Session.serve()` method that reads,
   loops, dispatches, and writes all at once.
2. **Small single-purpose units** with well-defined interfaces. If a file does two things, split it.
3. **Module boundaries are law:**
   - `buffer`, `crypto` — leaf libraries with **zero** protocol knowledge (no opcodes, no revision).
   - `net-core` — **revision-agnostic**: `Prot(opcode,size)`, `Message` hierarchy, codec
     interfaces + registry, `ProtocolStage`, `Session`. **No opcode literals, ever.**
   - `cache` — layered: `Store` leaf → `Container`(decompress/XTEA) → `Js5Index` → `Definition`.
     Each layer a small pure/testable unit, not one god-class. Build upper layers as needed (YAGNI).
   - `protocol-osrs239` — owns the rev-239 opcode/size table and per-packet codecs.
   - services (`gateway`, later `login`/`world`/`social`) — thin: wire codecs + handlers onto
     `ProtocolStage`; never hand-roll wire parsing.
   - Dependencies point *down* the stack; leaves never depend on services.
4. **Codec-registry pattern** for packets: immutable message data classes + tiny
   `MessageDecoder`/`MessageEncoder` units bound into an immutable registry. Moving revisions =
   swap the codec set, not rewrite logic.
5. **No magic numbers.** Opcodes come from `Prot` tables; content ids come from RSCM `name=id`
   maps (when content arrives). No raw id/opcode literals in game/service code.
5a. **Packet composition (informed by rsmod/void — see the design doc).** One small file per
    packet: a decoder, an encoder, and (for inbound) a **handler** — never a giant packet file.
    Route decoded messages through a **type-keyed `HandlerRepository`** (message `Class` →
    handler), NEVER a growing `when(message){...}` god-method. Handlers declare their own
    dependencies via constructor args.
5b. **Packaging — concern sub-packages, never giga-folders.** Within a protocol domain, split by
    concern: `<domain>/prot`, `<domain>/message`, `<domain>/codec`. Never a flat folder mixing
    messages + encoders + prot + wiring. Inbound **handlers live a layer up** in the service
    (gateway), not beside the protocol codecs. Organize by **domain** (`js5`, `login`, `game/…`)
    so it scales to hundreds of packets.
5c. **Registration via DI factories, not binding chains.** Codecs/handlers are collected via
    **Koin** (each declared once in its domain's Koin module; the `CodecRepository`/
    `HandlerRepository` built by `getAll<...>()`), so there is NO chained `bindEncoder(...)`
    growing at one site. **Koin lives only in the service layer; `net-core` stays
    framework-agnostic** (its registries are plain maps populated by the service). Registration
    stays explicit-per-packet (a module declaration each), never reflection/classpath scanning.

## Code style

6. **KDoc, not comment-scatter.** Document intent/contract with `/** ... */` doc comments on
   declarations (class/function/property). AVOID clusters of inline `//` comments. An inline
   comment is only for a non-obvious constraint that cannot live in a doc comment.
7. **Proper logger, never `println`.** Use slf4j + logback via `KotlinLogging.logger {}`. Levels:
   DEBUG = wire/packet detail; INFO = lifecycle; WARN = anomalies; ERROR = exceptions (pass the
   throwable). No `System.out`/`printStackTrace` in `src/main`.
8. Match surrounding style; keep files focused; prefer clarity over cleverness.

## Networking — efficient AND hardened

9. Fixed/bounded reads; reject malformed/oversized input; **close the connection on every exit
   path** (`finally`), while catching per-connection exceptions so the accept loop survives.
10. No unbounded per-connection allocation. Read/idle timeouts at the internet-facing edge. The
    tick loop (when it exists) is single-threaded and deterministic; only thread-safe queues cross
    the network↔game boundary.
11. Keep the connection-dispatch composition clean and DRY — hoist immutable registries; build
    per-connection state (ISAAC/XOR ciphers) per connection.

## Security & privacy — non-negotiable

12. **NEVER use the user's real RuneScape/RuneLite account.** Launch the client only with an
    **isolated `user.home`** (throwaway dir) so it cannot read `~/.runelite`; only DUMMY
    credentials. Never read or modify the user's credentials files.
13. **NEVER log credentials** (password/username) or retain them. Auto-accept logins for now
    without storing the plaintext.
14. RSA private key stays server-side (gitignored `server-rsa.properties`); never commit keys,
    the cache dump, the client tree, or screenshots (all gitignored).

## Revision pinning

15. Pinned to **rev 239** (client = self-built `client/runelite`; cache = OpenRS2 build 239;
    gateway handshake = 239). We self-host JS5 — we never contact Jagex's update server. A new
    Jagex revision does not affect us; moving revisions is a deliberate build step (bump
    `TARGET_BUILD`, re-fetch cache, bump the gateway revision, re-run the opcode/RSA recon).

## Process

16. **TDD.** Write the failing test first; every change ships with tests; keep the whole suite
    green (`./gradlew build`). Wire/protocol behavior is validated against the **real client** —
    the client is the oracle when the decompile and the wire disagree.
17. **Never change wire behavior in a cleanup/refactor.** Byte-for-byte tests must still pass.
18. Non-trivial changes get a review pass (spec compliance + code quality) before merge.
19. Git: feature branch → `staging` (validated there) → `main`. Don't push without explicit
    go-ahead.

## Ground-truth docs

- Design: `docs/superpowers/specs/`. Protocol facts: `docs/superpowers/research/`.
- Architecture rationale: `docs/superpowers/research/2026-07-14-networking-architecture-lessons.md`.
