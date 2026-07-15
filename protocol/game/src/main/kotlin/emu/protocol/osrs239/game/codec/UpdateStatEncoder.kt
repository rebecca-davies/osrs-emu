package emu.protocol.osrs239.game.codec

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot
import emu.protocol.osrs239.game.message.UpdateStat
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes invisible/current level, p1Alt1 stat index, and p4Alt2 experience. */
object UpdateStatEncoder : MessageEncoder<UpdateStat> {
    override val prot: Prot = GameServerProt.UPDATE_STAT
    override val messageType = UpdateStat::class.java
    override fun encode(cipher: StreamCipher, message: UpdateStat): ByteArray =
        JagexBuffer.alloc(7).apply {
            writeByte(message.invisibleBoostedLevel)
            writeByte(message.currentLevel)
            writeByteAlt1(message.stat)
            writeIntAlt2(message.experience)
        }.array
}
