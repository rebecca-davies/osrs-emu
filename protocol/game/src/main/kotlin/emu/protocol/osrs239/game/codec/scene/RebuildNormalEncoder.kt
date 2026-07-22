package emu.protocol.osrs239.game.codec.scene

import emu.protocol.osrs239.game.message.scene.RebuildNormal
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes the six-byte, post-login form of rev-239's normal scene rebuild. */
object RebuildNormalEncoder : CipherIndependentMessageEncoder<RebuildNormal> {
    override val prot: Prot = GameServerProt.REBUILD_NORMAL
    override val messageType = RebuildNormal::class.java

    override fun encode(message: RebuildNormal): ByteArray =
        RebuildNormalBodyEncoder.encode(
            centreZoneX = message.centreZoneX,
            centreZoneY = message.centreZoneY,
            worldArea = message.worldArea,
        )
}
