package emu.gateway.js5

import emu.protocol.osrs239.js5.prot.Js5Prot
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeByte

private val logger = KotlinLogging.logger {}

/**
 * Reads the 20-byte JS5 handshake payload (revision int + 4 key ints) that follows opcode 15 and
 * replies with a single status byte: `0` (ok) if [revision] matches the client's, else `6` (client
 * out of date). Returns whether the caller should proceed to the JS5 [emu.netcore.pipeline.ProtocolStage].
 *
 * The default of 239 matches the current RuneLite injected-client (1.12.33-SNAPSHOT); a freshly
 * cloned RuneLite tracks live OSRS and had bumped past the originally-assumed rev 235.
 */
suspend fun performHandshake(read: ByteReadChannel, write: ByteWriteChannel, revision: Int = 239): Boolean {
    val hs = ByteArray(Js5Prot.HANDSHAKE.size)
    read.readFully(hs)
    val clientRev = ((hs[0].toInt() and 0xFF) shl 24) or ((hs[1].toInt() and 0xFF) shl 16) or
        ((hs[2].toInt() and 0xFF) shl 8) or (hs[3].toInt() and 0xFF)
    return if (clientRev == revision) {
        logger.debug { "JS5 handshake: client revision=$clientRev matches expected $revision" }
        write.writeByte(0); write.flush(); true
    } else {
        logger.warn { "JS5 handshake: client revision=$clientRev does not match expected $revision; rejecting" }
        write.writeByte(6); write.flush(); false
    }
}
