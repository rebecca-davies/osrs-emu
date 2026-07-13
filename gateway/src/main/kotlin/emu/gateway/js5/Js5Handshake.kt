package emu.gateway.js5

import emu.protocol.osrs235.js5.Js5Prot
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeByte

// Reads the 20-byte JS5 handshake payload (revision int + 4 key ints) after opcode 15.
// Replies 0 (ok) if the revision matches, else 6 (client out of date). Returns whether to proceed.
//
// Revision 239 matches the current RuneLite injected-client (1.12.33-SNAPSHOT). A freshly cloned
// RuneLite tracks live OSRS and had bumped past the originally-assumed rev 235 to 239.
suspend fun performHandshake(read: ByteReadChannel, write: ByteWriteChannel, revision: Int = 239): Boolean {
    val hs = ByteArray(Js5Prot.HANDSHAKE.size)
    read.readFully(hs)
    val clientRev = ((hs[0].toInt() and 0xFF) shl 24) or ((hs[1].toInt() and 0xFF) shl 16) or
        ((hs[2].toInt() and 0xFF) shl 8) or (hs[3].toInt() and 0xFF)
    println("JS5 handshake: client revision=$clientRev (gateway expects $revision)")
    return if (clientRev == revision) {
        write.writeByte(0); write.flush(); true
    } else {
        write.writeByte(6); write.flush(); false
    }
}
