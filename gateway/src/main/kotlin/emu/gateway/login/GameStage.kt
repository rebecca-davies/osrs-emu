package emu.gateway.login

import emu.crypto.IsaacCipher
import emu.netcore.codec.CodecRepository
import emu.netcore.pipeline.OutboundSession
import emu.protocol.osrs239.game.PlayerAppearance
import emu.protocol.osrs239.game.PlayerInfo
import emu.protocol.osrs239.game.RebuildNormal
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

/**
 * Idle-read deadline for a connection that has reached the GAME stage (a logged-in client). Unlike
 * [emu.gateway.HANDSHAKE_TIMEOUT], this is deliberately generous: a real client can sit idle between
 * player-initiated packets, and this deadline resets on every packet [runGameStage] receives (see
 * its doc), so it only fires on a connection that stops sending anything at all for the whole
 * window — the CLAUDE.md §10 "read/idle timeouts at the edge" requirement, applied without
 * penalizing a normally-active client.
 */
val GAME_IDLE_TIMEOUT: Duration = 30.seconds

/** Lumbridge, the fixed milestone-3 spawn tile for every newly logged-in local player. */
private const val SPAWN_PLANE = 0
private const val SPAWN_X = 3222
private const val SPAWN_Y = 3218

/**
 * Milestone-5 game stage: proactively pushes the initial scene — [RebuildNormal] then [PlayerInfo]
 * for the local player ([LOCAL_PLAYER_INDEX], per docs/superpowers/research/2026-07-14-rev239-
 * ingame-facts.md §3/§4a) — over [write], ISAAC-adjusted via [outboundCipher], then holds the
 * connection reading/discarding inbound bytes exactly as milestone-3 did.
 *
 * The two initial packets are sent through [OutboundSession] — itself a thin wrapper over
 * [emu.netcore.pipeline.writePacket] and the shared [gameCodecs] registry — so this function never
 * hand-rolls opcode/body bytes (CLAUDE.md §1/§9). This is a proactive push, not a reply to any
 * inbound packet, which is why it happens unconditionally before the read loop rather than from a
 * handler.
 *
 * We do NOT yet have a rev-239 game-opcode size table for *inbound* packets (that arrives with real
 * game-packet decoding), so this cannot correctly frame-and-discard each inbound packet's payload
 * the way [emu.netcore.pipeline.ProtocolStage] does for JS5/login. Instead it decrypts and logs just
 * the opcode byte for visibility, then keeps reading. This is intentionally the smallest thing that
 * satisfies the milestone: the gateway never closes this socket itself for a normally-active
 * client — it only stops when the client disconnects (the loop's `readByte()` throws on EOF, which
 * the caller's try/finally turns into a clean `conn.close()`) or when [idleTimeout] elapses with no
 * inbound byte at all (a slowloris connection that logged in and then never sends another packet,
 * per CLAUDE.md §10). The deadline resets on every byte received, so an active-but-slow client is
 * never penalized — only true idleness past [idleTimeout] closes the connection. Replace this with
 * proper per-opcode payload sizes when inbound game-packet decoding is implemented.
 */
suspend fun runGameStage(
    read: ByteReadChannel,
    write: ByteWriteChannel,
    inboundCipher: IsaacCipher,
    outboundCipher: IsaacCipher,
    gameCodecs: CodecRepository,
    idleTimeout: Duration = GAME_IDLE_TIMEOUT,
) {
    sendInitialScene(write, outboundCipher, gameCodecs)

    while (true) {
        val raw = withTimeoutOrNull(idleTimeout) { read.readByte() } ?: run {
            logger.info { "game stage: idle for $idleTimeout with no inbound packet; closing connection" }
            return
        }
        val opcode = ((raw.toInt() and 0xFF) - inboundCipher.nextInt()) and 0xFF
        logger.debug { "game stage: inbound opcode $opcode (payload framing not implemented until the game-packet milestone)" }
    }
}

/**
 * Sends the local player's initial scene — first [RebuildNormal] (the scene/map rebuild that puts
 * the client in-world), then [PlayerInfo] (the local avatar, appearance-only) — via [outboundCipher]
 * so the client's `(rawOpcode - outboundIsaac.nextInt()) and 0xFF` unscrambling lines up. Both
 * packets go through the exact same [OutboundSession] instance so they share one outbound ISAAC
 * keystream position, in the order the client's login state machine expects them (ingame-facts.md
 * §3a/§4a).
 */
private suspend fun sendInitialScene(write: ByteWriteChannel, outboundCipher: IsaacCipher, gameCodecs: CodecRepository) {
    val session = OutboundSession(gameCodecs, outboundCipher, write)
    logger.info { "game stage: sending initial scene (RebuildNormal + PlayerInfo) for local player index $LOCAL_PLAYER_INDEX" }
    session.send(RebuildNormal(plane = SPAWN_PLANE, x = SPAWN_X, y = SPAWN_Y))
    session.send(PlayerInfo(PlayerAppearance()))
}
