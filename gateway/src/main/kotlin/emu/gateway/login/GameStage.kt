package emu.gateway.login

import emu.crypto.IsaacCipher
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readByte

/**
 * Milestone-3 game stage: proves "logged in, connection held" (the correct black screen) rather
 * than processing real game packets (milestone 5). Per rev239-login-facts.md §4/§6 and the plan,
 * every inbound byte from here on is ISAAC-obfuscated: the wire opcode is
 * `(rawByte - inboundCipher.nextInt()) and 0xFF`.
 *
 * We do NOT yet have a rev-239 game-opcode size table (that arrives with real game-packet
 * decoding), so this cannot correctly frame-and-discard each packet's payload the way
 * `ProtocolStage` does for JS5/login. Instead it decrypts and logs just the opcode byte for
 * visibility, then keeps reading. This is intentionally the smallest thing that satisfies the
 * milestone: the gateway never closes this socket itself — it only stops when the client
 * disconnects (the loop's `readByte()` throws on EOF, which the caller's try/finally turns into a
 * clean `conn.close()`). Replace this with proper per-opcode payload sizes when the game protocol
 * is implemented.
 */
suspend fun runGameStage(read: ByteReadChannel, inboundCipher: IsaacCipher) {
    while (true) {
        val raw = read.readByte().toInt() and 0xFF
        val opcode = (raw - inboundCipher.nextInt()) and 0xFF
        println("game stage: inbound opcode $opcode (payload framing not implemented until the game-packet milestone)")
    }
}
