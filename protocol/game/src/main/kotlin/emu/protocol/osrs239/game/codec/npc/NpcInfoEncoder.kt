package emu.protocol.osrs239.game.codec.npc

import emu.buffer.BitBuf
import emu.protocol.osrs239.game.message.npc.NpcInfo
import emu.protocol.osrs239.game.message.npc.NpcInfoAddition
import emu.protocol.osrs239.game.message.npc.NpcInfoLocal
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes rev-239's small-coordinate NPC local-list and addition bit stream. */
object NpcInfoEncoder : CipherIndependentMessageEncoder<NpcInfo> {
    override val prot: Prot = GameServerProt.NPC_INFO
    override val messageType = NpcInfo::class.java

    override fun encode(message: NpcInfo): ByteArray {
        val bits = BitBuf()
        bits.writeBits(8, message.locals.size)
        message.locals.forEach { local ->
            when (local) {
                NpcInfoLocal.Idle -> bits.writeBits(1, 0)
                is NpcInfoLocal.Walk -> {
                    bits.writeBits(1, 1).writeBits(2, WALK)
                    bits.writeBits(3, local.direction).writeBits(1, 0)
                }
                NpcInfoLocal.Remove -> bits.writeBits(1, 1).writeBits(2, REMOVE)
            }
        }
        message.additions.forEach { addition -> writeAddition(bits, addition) }
        return bits.toByteArray()
    }

    private fun writeAddition(bits: BitBuf, addition: NpcInfoAddition) {
        bits.writeBits(16, addition.index)
        bits.writeBits(1, 0)
        bits.writeBits(1, 0)
        bits.writeBits(6, addition.deltaX)
        bits.writeBits(3, addition.orientation)
        bits.writeBits(6, addition.deltaY)
        bits.writeBits(1, 1)
        bits.writeBits(14, addition.type)
    }

    private const val WALK = 1
    private const val REMOVE = 3
}
