package emu.protocol.osrs239.game.codec

import emu.crypto.StreamCipher
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot
import emu.protocol.osrs239.game.message.RebuildNormal
import emu.protocol.osrs239.game.prot.GameServerProt

/** Encodes the six-byte, post-login form of rev-239's normal scene rebuild. */
object RebuildNormalEncoder : MessageEncoder<RebuildNormal> {
    override val prot: Prot = GameServerProt.REBUILD_NORMAL
    override val messageType = RebuildNormal::class.java

    override fun encode(cipher: StreamCipher, message: RebuildNormal): ByteArray =
        RebuildNormalBodyEncoder.encode(
            centreZoneX = message.centreZoneX,
            centreZoneY = message.centreZoneY,
            worldArea = message.worldArea,
        )
}
