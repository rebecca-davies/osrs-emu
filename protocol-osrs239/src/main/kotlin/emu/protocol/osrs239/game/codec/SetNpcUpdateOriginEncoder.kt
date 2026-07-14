package emu.protocol.osrs239.game.codec

import emu.crypto.StreamCipher
import emu.netcore.codec.MessageEncoder
import emu.netcore.prot.Prot
import emu.protocol.osrs239.game.message.SetNpcUpdateOrigin
import emu.protocol.osrs239.game.prot.GameServerProt

/**
 * Encodes [SetNpcUpdateOrigin] (opcode 116), a fixed 2-byte body `[originX, originZ]` (each u8) —
 * exactly what the rev-239 client's SetNpcUpdateOrigin reader consumes (`originX = g1`,
 * `originZ = g1`, then base coord = `(baseX + originX, baseZ + originZ)`; no transforms). Never
 * touches [cipher] (the opcode's ISAAC adjustment is applied by the pipeline).
 */
object SetNpcUpdateOriginEncoder : MessageEncoder<SetNpcUpdateOrigin> {
    override val prot: Prot = GameServerProt.SET_NPC_UPDATE_ORIGIN
    override val messageType = SetNpcUpdateOrigin::class.java

    override fun encode(cipher: StreamCipher, message: SetNpcUpdateOrigin): ByteArray =
        byteArrayOf((message.originX and 0xFF).toByte(), (message.originZ and 0xFF).toByte())
}
