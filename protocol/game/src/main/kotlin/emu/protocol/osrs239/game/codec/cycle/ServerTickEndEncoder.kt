package emu.protocol.osrs239.game.codec.cycle

import emu.protocol.osrs239.game.message.cycle.ServerTickEnd
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/**
 * Encodes [ServerTickEnd] (opcode 83): a fixed-size, **empty-body** packet — the per-tick
 * terminator. The pipeline writes the (ISAAC-adjusted) opcode with no length and no payload, exactly
 * like rsprot's `NoOpMessageEncoder`. The opcode's own ISAAC adjustment
 * is applied by [emu.transport.pipeline.outbound.writePacket]).
 */
object ServerTickEndEncoder : CipherIndependentMessageEncoder<ServerTickEnd> {
    override val prot: Prot = GameServerProt.SERVER_TICK_END
    override val messageType = ServerTickEnd::class.java

    override fun encode(message: ServerTickEnd): ByteArray = ByteArray(0)
}
