package emu.gateway.login

import emu.crypto.IsaacCipher
import emu.netcore.codec.CodecRepository
import emu.netcore.pipeline.OutboundSession
import emu.protocol.osrs239.game.message.PlayerAppearance
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/**
 * Idle-read deadline for a connection that has reached the GAME stage (a logged-in client). Unlike
 * [emu.gateway.HANDSHAKE_TIMEOUT], this is deliberately generous: a real client can sit idle between
 * player-initiated packets, and this deadline resets on every packet the inbound drain receives, so
 * it only fires on a connection that stops sending anything at all for the whole window — the
 * CLAUDE.md §10 "read/idle timeouts at the edge" requirement, applied without penalizing a
 * normally-active client. Note this bounds only *inbound* silence; the server still emits its
 * per-tick PLAYER_INFO heartbeat the whole time (see [GameLoop]).
 */
val GAME_IDLE_TIMEOUT: Duration = 30.seconds

/** Lumbridge, the fixed milestone-3 spawn tile for every newly logged-in local player. */
private const val SPAWN_PLANE = 0
private const val SPAWN_X = 3222
private const val SPAWN_Y = 3218

/**
 * Milestone-5 game stage: puts the client in-world and then keeps it there.
 *
 * On entry it sends [sendInitialGameCycle]: the Lumbridge rebuild plus the capture-shaped atomic
 * entity/player/NPC/zone group and neutral rev-239 frame/account state. It then runs two sibling
 * coroutines for the connection's lifetime — the network↔game split the research doc mandates:
 *  - a **tick loop** ([GameLoop]) that sends an atomic idle player/NPC group every 600ms; this steady
 *    cycle stream keeps the client in IN_GAME after the initial cycle renders the terrain.
 *  - an **inbound drain** ([drainInbound]) that keeps the socket read side clear and enforces the
 *    idle timeout.
 *
 * When either side ends (the client disconnects, or it goes idle past [idleTimeout]) the other is
 * cancelled and the connection is handed back to [emu.gateway.handleConnection]'s `finally` to close.
 *
 * Every server->client packet is written through the same
 * [OutboundSession] — a thin wrapper over [emu.netcore.pipeline.writePacket] and the shared
 * [gameCodecs] registry — so each advances the outbound ISAAC keystream exactly once for its opcode
 * and this function never hand-rolls opcode/body bytes (CLAUDE.md §1/§9).
 *
 * [tickInterval] and [maxTicks] default to production values (600ms, unbounded); tests inject a
 * short interval and a small tick cap to drive a handful of heartbeats without a real-time sleep.
 */
suspend fun runGameStage(
    read: ByteReadChannel,
    write: ByteWriteChannel,
    inboundCipher: IsaacCipher,
    outboundCipher: IsaacCipher,
    gameCodecs: CodecRepository,
    idleTimeout: Duration = GAME_IDLE_TIMEOUT,
    tickInterval: Duration = TICK_INTERVAL,
    maxTicks: Int = Int.MAX_VALUE,
): Unit = coroutineScope {
    val session = OutboundSession(gameCodecs, outboundCipher, write)
    val appearance = if (System.getenv("EMU_NO_APPEARANCE") == "1") null else PlayerAppearance()
    sendInitialGameCycle(
        session = session,
        spawnPlane = SPAWN_PLANE,
        spawnX = SPAWN_X,
        spawnY = SPAWN_Y,
        localPlayerIndex = LOCAL_PLAYER_INDEX,
        appearance = appearance,
    )

    // Diagnostic toggle (milestone-5 investigation): EMU_SKIP_TICKS=1 sends the initial scene and
    // then goes silent (no per-tick PLAYER_INFO), to isolate whether the post-login drop is caused
    // by a per-tick packet or the login/rebuild itself.
    val skipTicks = System.getenv("EMU_SKIP_TICKS") == "1"
    if (skipTicks) {
        logger.info { "game stage: EMU_SKIP_TICKS=1 — sending scene only, no per-tick heartbeat" }
        drainInbound(read, inboundCipher, idleTimeout)
        return@coroutineScope
    }

    val tickJob = launch { GameLoop(session, tickInterval).run(maxTicks) }
    val readJob = launch {
        drainInbound(read, inboundCipher, idleTimeout)
        // The client is gone / idle: stop the heartbeat so this connection can close.
        tickJob.cancel()
    }

    // Complete when the heartbeat ends — either the reader cancelled it (client gone / idle) or it
    // hit its tick cap (tests) — then stop the reader if it is still blocked awaiting a byte.
    tickJob.join()
    readJob.cancel()
}

/**
 * Keeps the connection's read side drained for the whole GAME stage: blocks on each inbound byte,
 * decrypts just the opcode ([inboundCipher]) for visibility, and discards it. Returns — ending the
 * stage — when the client disconnects (`readByte` throws on EOF) or when [idleTimeout] elapses with
 * no inbound byte at all (a slowloris connection that logged in and then never sends another packet,
 * CLAUDE.md §10). The deadline resets on every byte received, so an active-but-slow client is never
 * penalized.
 *
 * We do NOT yet have a rev-239 game-opcode size table for *inbound* packets (that arrives with real
 * game-packet decoding), so this cannot frame-and-discard each inbound packet's payload the way
 * [emu.netcore.pipeline.ProtocolStage] does for JS5/login — it decrypts one ISAAC int per byte,
 * which will not stay opcode-aligned with the client, but that is harmless here: nothing consumes
 * the decoded opcode and the client never validates our inbound keystream. The point is only to keep
 * reading so the socket buffer never backs up. Replace with proper per-opcode payload sizes when
 * inbound game-packet decoding lands.
 */
private suspend fun drainInbound(read: ByteReadChannel, inboundCipher: IsaacCipher, idleTimeout: Duration) {
    while (true) {
        val raw = try {
            withTimeoutOrNull(idleTimeout) { read.readByte() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.info { "game stage: inbound read ended (${e.javaClass.simpleName}: ${e.message}); ending stage" }
            return
        } ?: run {
            logger.info { "game stage: idle for $idleTimeout with no inbound packet; closing connection" }
            return
        }
        val opcode = ((raw.toInt() and 0xFF) - inboundCipher.nextInt()) and 0xFF
        logger.debug { "game stage: inbound opcode $opcode (payload framing not implemented until the game-packet milestone)" }
    }
}
