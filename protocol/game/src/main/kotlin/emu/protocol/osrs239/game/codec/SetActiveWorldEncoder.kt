package emu.protocol.osrs239.game.codec

import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.game.message.SetActiveWorld
import emu.protocol.osrs239.game.prot.GameServerProt

/**
 * Encodes [SetActiveWorld] (opcode 47), a fixed 3-byte body: a big-endian signed u16 world [index]
 * (`0` = root world) followed by a u8 active level — exactly what the rev-239 client's
 * `SetActiveWorldV2` reader consumes (`index = g2s`, `activeLevel = g1`; no byte transforms).
 *
 * Per [emu.transport.pipeline.writePacket]'s keystream-ordering contract the opcode's ISAAC
 * adjustment is applied by the pipeline, so this never touches [cipher].
 */
object SetActiveWorldEncoder : MessageEncoder<SetActiveWorld> {
    override val prot: Prot = GameServerProt.SET_ACTIVE_WORLD
    override val messageType = SetActiveWorld::class.java

    override fun encode(cipher: StreamCipher, message: SetActiveWorld): ByteArray =
        byteArrayOf(
            (message.index ushr 8).toByte(),
            (message.index and 0xFF).toByte(),
            (message.activeLevel and 0xFF).toByte(),
        )
}
