package emu.protocol.osrs239.game.codec

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot
import emu.protocol.osrs239.game.message.RunClientScript
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes the client-script signature, reverse-ordered argument payload, and script id. */
object RunClientScriptEncoder : MessageEncoder<RunClientScript> {
    override val prot: Prot = GameServerProt.RUN_CLIENT_SCRIPT
    override val messageType = RunClientScript::class.java

    override fun encode(cipher: StreamCipher, message: RunClientScript): ByteArray {
        val argumentSize = message.arguments.sumOf {
            when (it) {
                is Int -> Int.SIZE_BYTES
                is String -> it.toByteArray(CP_1252).size + 1
                else -> error("RunClientScript validates argument types")
            }
        }
        return JagexBuffer.alloc(message.arguments.size + 1 + argumentSize + Int.SIZE_BYTES).apply {
            writeCString(message.arguments.joinToString(separator = "") { if (it is String) "s" else "i" })
            for (argument in message.arguments.asReversed()) {
                when (argument) {
                    is Int -> writeInt(argument)
                    is String -> writeCString(argument)
                }
            }
            writeInt(message.id)
        }.array
    }

    private val CP_1252 = charset("windows-1252")
}
