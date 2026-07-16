package emu.protocol.osrs239.game.codec.camera

import emu.buffer.JagexBuffer
import emu.crypto.StreamCipher
import emu.protocol.osrs239.game.message.camera.CamTargetPlayer
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot

/** Encodes rev-239's player variant of `CAM_TARGET_V4`. */
object CamTargetPlayerEncoder : MessageEncoder<CamTargetPlayer> {
    override val prot: Prot = GameServerProt.CAM_TARGET_V4
    override val messageType = CamTargetPlayer::class.java

    override fun encode(cipher: StreamCipher, message: CamTargetPlayer): ByteArray =
        JagexBuffer.alloc(5).apply {
            writeInt(message.playerIndex)
            writeByteAlt2(PLAYER_TARGET_TYPE)
        }.array

    private const val PLAYER_TARGET_TYPE = 0
}
