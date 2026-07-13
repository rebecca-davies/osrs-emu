package emu.gateway.login

import emu.crypto.IsaacCipher
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.ByteReadChannel
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

/**
 * Milestone-3 game stage: proves "logged in, connection held" (the correct black screen) rather
 * than processing real game packets (milestone 5). Per rev239-login-facts.md §4/§6 and the plan,
 * every inbound byte from here on is ISAAC-obfuscated: the wire opcode is
 * `(rawByte - inboundCipher.nextInt()) and 0xFF`.
 *
 * We do NOT yet have a rev-239 game-opcode size table (that arrives with real game-packet
 * decoding), so this cannot correctly frame-and-discard each packet's payload the way
 * [emu.netcore.pipeline.ProtocolStage] does for JS5/login. Instead it decrypts and logs just the
 * opcode byte for visibility, then keeps reading. This is intentionally the smallest thing that
 * satisfies the milestone: the gateway never closes this socket itself for a normally-active
 * client — it only stops when the client disconnects (the loop's `readByte()` throws on EOF, which
 * the caller's try/finally turns into a clean `conn.close()`) or when [idleTimeout] elapses with no
 * inbound byte at all (a slowloris connection that logged in and then never sends another packet,
 * per CLAUDE.md §10). The deadline resets on every byte received, so an active-but-slow client is
 * never penalized — only true idleness past [idleTimeout] closes the connection. Replace this with
 * proper per-opcode payload sizes when the game protocol is implemented.
 */
suspend fun runGameStage(read: ByteReadChannel, inboundCipher: IsaacCipher, idleTimeout: Duration = GAME_IDLE_TIMEOUT) {
    while (true) {
        val raw = withTimeoutOrNull(idleTimeout) { read.readByte() } ?: run {
            logger.info { "game stage: idle for $idleTimeout with no inbound packet; closing connection" }
            return
        }
        val opcode = ((raw.toInt() and 0xFF) - inboundCipher.nextInt()) and 0xFF
        logger.debug { "game stage: inbound opcode $opcode (payload framing not implemented until the game-packet milestone)" }
    }
}
