# osrsemu — engineering rules (every agent MUST follow)

An OSRS server emulator that RuneLite connects to and logs into. These rules are binding for
every task and every subagent. When dispatching an agent, point it here. Violations should fail
review.

## Architecture — composable, never monolithic

1. **No god-objects.** A connection "session" is a *composition* — a decoder + a pure handler +
   an encoder driven by `transport`'s `ProtocolStage`. Never a `Session.serve()` method that reads,
   loops, dispatches, and writes all at once.
2. **Small cohesive units** with well-defined interfaces. Split independently changing
   responsibilities; keep a tightly coupled closed model family, codec pair, or product/builder
   together when separating it would only manufacture navigation overhead.
3. **Module boundaries are law:**
   - `libraries/{buffer,compression,crypto}` — leaf libraries with **zero** protocol knowledge
     (no opcodes, no revision).
   - `libraries/transport` — **revision-agnostic**: `Prot(opcode,size)`, directional message
     contracts, codec/handler registries, packet framing, and `ProtocolStage`. It does not own
     login/game sessions. **No opcode literals, ever.**
   - `cache` — layered: `Store` leaf → `Container`(decompress/XTEA) → `Js5Index` → `Definition`.
     Each layer a small pure/testable unit, not one god-class. Build upper layers as needed (YAGNI).
   - `protocol/{login,js5,game}` — owns the rev-239 opcode/size tables and per-packet codecs.
   - `server/gateway`, `server/login`, `server/js5`, `server/game` — peer services with no peer
     dependencies. Gateway accepts and routes sockets; each protocol service owns its stage.
   - Login owns authentication policy and bcrypt under `server/login/auth`; do not create a separate
     identity service. Persistence owns storage, not authentication decisions.
   - `server/session` — framework-free handoff contracts shared by the peer services.
   - `server/host` — the only composition root, process entry point, DI owner and environment reader.
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
    messages + encoders + prot + wiring. Inbound **handlers live a layer up** in the owning service,
    not beside the protocol codecs. Organize by **domain** (`js5`, `login`, `game/…`)
    so it scales to hundreds of packets.
5c. **Registration is explicit and framework-free.** Protocols expose named repository factories;
    services constructor-inject handlers and connection-local state. Koin and environment reads
    live only in `server/host`; peer services and `transport` use ordinary Kotlin constructors.
5d. **Cohesion is enforced.** One primary responsibility per file. Reusable or independently
    changing logic gets a named subpackage and its own file. Never create `Util`, `Common`, or
    unrelated config companion-object dumps. Keep wire, handler, domain, persistence and
    orchestration packages separate. Gradle modules live under capability folders such as
    `libraries/`, `protocol/`, `server/`, and `tools/`; review dependencies and ownership as part
    of architecture work instead of enforcing file layout with source-scanning validators.
5e. **One independently meaningful production responsibility per file.** `internal` is visibility,
    not permission to hide unrelated classes in a convenient file. Closely coupled declarations
    may share a file when they form one closed model family, codec capability, or product/builder
    and change for the same reason. Do not create a file for every trivial helper.
    Interfaces belong at capability and substitutable external seams, not in front of private pure
    helpers. Companion objects contain only factories or conversions intrinsic to their owning
    type; environment keys, service defaults, SQL, migrations, registries, caches and unrelated
    constants belong to typed configuration, named catalogs, or file-private implementation values.
5f. **Put intrinsic state operations on their natural domain receiver.** An operation that only
    maintains a player's own invariants should read as `player.closeModal()` or
    `player.discardActiveScript()`, not as a pass-through process function taking that player.
    Gameplay policy stays in feature-local content, and coordinators/processes remain responsible
    for external capabilities and cross-object ordering. Never inject services into `Player`
    merely for fluent syntax.

## Architecture vocabulary

- **Game server** is the peer service under `server/game`. **World** means only its authoritative,
  single-threaded game state and cycle. Never call the service/module a world server.
- **Service** is the narrow host-facing capability at a module boundary. **Server** is its concrete
  concurrency and lifecycle owner. **Listener** owns a bound socket and accept loop. **Runner**
  advances one bounded execution lifetime, such as a connection or script. **Coordinator** is
  host-only sequencing across peer services.
- **Connection** is the socket/channel lifetime. **Session** is authenticated protocol-lifetime
  data composed around a connection; it is never a read/dispatch/write god-object. **Handoff** is
  the one-way transfer from login to game. **Reservation** temporarily owns a world slot before the
  player attaches.
- **World** owns live entities and indexes. **Cycle** is one deterministic 600 ms world update.
  **Process** is one ordered phase within that cycle. **Runtime** schedules cycles; **lifecycle**
  starts, stops, and reports failure for that runtime.
- **Prot** is Jagex opcode/size metadata. **Message** is an immutable decoded/encodable Kotlin
  value. **Codec** converts a message body to or from bytes. **Packet** means the encoded wire frame.
  **Handler** maps one decoded inbound message into service work or a player action. **Output** maps
  world state into outbound messages/batches; it is not a codec.
- **Action** is RuneScape player work consumed by the strong/weak player action queues. **Input** is
  the validated payload carried by an action. **Player command** is authenticated developer-console
  work selected on the world thread. **World command** is cross-thread control work applied at the
  world boundary. **Queue** is an explicitly ordered, bounded producer/consumer structure; state
  whether it is a player action queue or an infrastructure queue.
- **IncomingPlayerActionQueue** is bounded connection-to-world staging. **PlayerActionQueue** is
  the RuneScape primary/strong/weak/engine queue. Keep the two lifetimes and semantics distinct.
- **Trigger** selects content; **script** is resumable content execution; **content** is gameplay
  behavior and configuration owned by `game`, never server bootstrap wiring.
- **Account** is authenticated identity and privilege, **character** is persisted gameplay data,
  and **player** is the live in-world entity. Do not use `Principal`.
- **Store** is an external byte/durable-data boundary. **Repository** is keyed lookup or dispatch
  over data/behavior. **Catalog** is an immutable known set. **Config** is immutable runtime policy.
  **Module** means host-only DI wiring, not a runtime object.
- Use the established words exactly. Do not introduce synonyms such as `Mailbox`, `Intent`, or
  `Admission`, and do not create vague `Manager`, `Util`, `Common`, or `Misc` buckets.

## Code style

6. **KDoc is contract-only.** Describe what callers need to know about a declaration, not the
   implementation history or thought process. Avoid comment scatter; use an inline comment only
   for a local non-obvious constraint.
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
12a. **The client MUST NOT reach Jagex's network.** RuneLite fetches Jagex's world list and pings
    every world on launch, independent of `jav_config` — repeatedly launching it hammers Jagex
    from the user's IP and trips a "login limit exceeded" rate limit that hits their REAL account.
    So: (1) **iterate HEADLESS** — verify protocol changes with a Kotlin test-client speaking our
    wire, never the GUI client (the client is acceptance, not the dev loop). (2) When a real-client
    screenshot is genuinely needed, launch it inside a **rootless network namespace**
    (`unshare -rn`) containing the gateway + local http + client on loopback only, so it physically
    cannot reach Jagex. (3) Launch the real client RARELY and never in a relaunch loop.
13. **NEVER log credentials** (password or username) or store plaintext passwords. Decode passwords
    into a short-lived `CharArray`, verify them with bcrypt, and clear the array immediately.
14. RSA private key stays server-side (gitignored `server-rsa.properties`); never commit keys,
    the cache dump, the client tree, or screenshots (all gitignored).

## Revision pinning

15. Pinned to **rev 239** (client = self-built `client/runelite`; cache = OpenRS2 build 239;
    login and JS5 protocols = 239). We self-host JS5 — we never contact Jagex's update server. A new
    Jagex revision does not affect us; moving revisions is a deliberate build step (bump
    `TARGET_BUILD`, re-fetch cache, bump protocol revisions, re-run the opcode/RSA recon).

## Process

16. **Test behavior, not repository shape.** Add focused tests for behavior and regressions; do not
    assert exact file names, declaration counts, package depth, or folder inventories. Keep the
    normal suite green (`./gradlew build`). Wire/protocol behavior is validated against the **real
    client** — the client is the oracle when the decompile and the wire disagree.
17. **Never change wire behavior in a cleanup/refactor.** Byte-for-byte tests must still pass.
18. **Independent blocking review is mandatory for every code-changing task.** Before each commit,
    dispatch a read-only agent that did not author the change to review the complete commit-sized
    diff. The reviewer must return `PASS` or `BLOCK` with file/line evidence against all of:
    responsibility, package ownership, object lifetime, direct dependencies, composition boundary,
    concurrency/queue bounds, global cycle ordering, extensibility, necessity, tests, declaration
    layout, and contract-only KDoc. A blocked diff is not committed. Fix every finding and request
    re-review. If the author disputes a finding, record the evidence in the external review log and
    return it to the independent reviewer; only a subsequent explicit `PASS` clears a `BLOCK`. The
    author can never overrule it. Never excuse transitional architecture or add a quality baseline
    entry to make the review pass. Repeat the review on the final aggregate diff before merge.
19. Git: isolated feature branch/worktree → validated merge to `main` → push to the canonical
    remote. Never revive the removed `staging` branch.
20. **Every concurrent subagent works in its OWN git worktree on its OWN branch — never two
    editing agents in one working tree.** Two agents sharing a checkout compile each other's
    half-written code (false build failures) and tangle commits. Create a worktree per workstream
    (`git worktree add <path> -b <branch>`), point each agent at its worktree path, and merge the
    branch back when the agent's task is validated. Only READ-ONLY agents (research/review) may
    run alongside an editor on the same tree.

## Project records

- Design, research, architecture notes, and assistant state live outside this code repository.
- Never add `docs/` or `.superpowers/` to this repository.
- Production comments explain their local invariant instead of depending on an external task
  narrative. When code and an approved design conflict, stop and reconcile them explicitly.
