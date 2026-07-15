package emu.server.js5.wire

import emu.protocol.osrs239.js5.prot.Js5Prot
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeByte

/** Validates one JS5 revision handshake and writes its success or out-of-date status byte. */
suspend fun performHandshake(
    read: ByteReadChannel,
    write: ByteWriteChannel,
    revision: Int = Js5Prot.REVISION,
): Boolean {
    val hs = ByteArray(Js5Prot.HANDSHAKE.size)
    read.readFully(hs)
    val clientRev = ((hs[0].toInt() and 0xFF) shl 24) or ((hs[1].toInt() and 0xFF) shl 16) or
        ((hs[2].toInt() and 0xFF) shl 8) or (hs[3].toInt() and 0xFF)
    return if (clientRev == revision) {
        write.writeByte(0)
        write.flush()
        true
    } else {
        write.writeByte(6)
        write.flush()
        false
    }
}
