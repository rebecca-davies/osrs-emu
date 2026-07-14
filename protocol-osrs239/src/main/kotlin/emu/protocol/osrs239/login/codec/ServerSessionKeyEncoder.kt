package emu.protocol.osrs239.login.codec

import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot
import emu.protocol.osrs239.login.message.ServerSessionKey
import emu.protocol.osrs239.login.prot.LoginProt

/**
 * Server->client reply to opcode 14: [1 status byte = 0][8-byte server session key, big-endian
 * long]. See docs/superpowers/research/2026-07-14-rev239-login-facts.md §1. This message carries no
 * wire opcode of its own — [LoginProt.OUTGOING] is a registry-only sentinel (same pattern as
 * `Js5Prot.GROUP_RESPONSE` / `Js5ResponseEncoder`); the gateway writes these bytes raw, ahead of any
 * ISAAC cipher (which does not exist yet at this point in the handshake).
 */
object ServerSessionKeyEncoder : MessageEncoder<ServerSessionKey> {
    override val prot: Prot = LoginProt.OUTGOING
    override val messageType = ServerSessionKey::class.java

    override fun encode(cipher: StreamCipher, message: ServerSessionKey): ByteArray {
        val out = ByteArray(9)
        out[0] = 0
        var k = message.key
        for (i in 8 downTo 1) {
            out[i] = k.toByte()
            k = k ushr 8
        }
        return out
    }
}
