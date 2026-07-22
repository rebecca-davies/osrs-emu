package emu.protocol.osrs239.game.codec.scene

import emu.protocol.osrs239.game.message.scene.WorldEntityInfo
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.CipherIndependentMessageEncoder
import emu.transport.prot.Prot

/** Encodes a zero high-resolution count and no low-resolution world entities. */
object WorldEntityInfoEncoder : CipherIndependentMessageEncoder<WorldEntityInfo> {
    override val prot: Prot = GameServerProt.WORLD_ENTITY_INFO
    override val messageType = WorldEntityInfo::class.java
    override fun encode(message: WorldEntityInfo): ByteArray = byteArrayOf(0)
}
