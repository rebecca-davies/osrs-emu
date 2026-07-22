package emu.server.bot.wire

import emu.crypto.IsaacCipher
import emu.protocol.osrs239.game.prot.GameClientProt
import emu.server.bot.behavior.BotClient
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeByte

/** Owns one bot connection's client packet channel and outgoing ISAAC sequence. */
internal class BotGamePacketWriter(
    private val write: ByteWriteChannel,
    private val cipher: IsaacCipher,
) : BotClient {
    override suspend fun walkTo(x: Int, z: Int) {
        writeEncryptedOpcode(GameClientProt.MOVE_GAMECLICK.opcode)
        write.writeByte(MOVE_GAMECLICK_BODY_SIZE.toByte())
        write.writeByte(x.toByte())
        write.writeByte((x ushr 8).toByte())
        write.writeByte(z.toByte())
        write.writeByte((z ushr 8).toByte())
        write.writeByte(NO_HELD_KEYS.toByte())
        write.flush()
    }

    private suspend fun writeEncryptedOpcode(opcode: Int) {
        write.writeByte(((opcode + cipher.nextInt()) and 0xFF).toByte())
    }

    private companion object {
        const val MOVE_GAMECLICK_BODY_SIZE = 5
        const val NO_HELD_KEYS = 128
    }
}
