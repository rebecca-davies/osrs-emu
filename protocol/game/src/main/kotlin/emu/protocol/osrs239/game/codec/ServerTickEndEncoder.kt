package emu.protocol.osrs239.game.codec

import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.game.message.ServerTickEnd
import emu.protocol.osrs239.game.prot.GameServerProt

/**
 * Encodes [ServerTickEnd] (opcode 83): a fixed-size, **empty-body** packet — the per-tick
 * terminator. The pipeline writes the (ISAAC-adjusted) opcode with no length and no payload, exactly
 * like rsprot's `NoOpMessageEncoder`. This never touches [cipher] (the opcode's own ISAAC adjustment
 * is applied by [emu.transport.pipeline.writePacket]).
 */
object ServerTickEndEncoder : MessageEncoder<ServerTickEnd> {
    override val prot: Prot = GameServerProt.SERVER_TICK_END
    override val messageType = ServerTickEnd::class.java

    override fun encode(cipher: StreamCipher, message: ServerTickEnd): ByteArray = ByteArray(0)
}
