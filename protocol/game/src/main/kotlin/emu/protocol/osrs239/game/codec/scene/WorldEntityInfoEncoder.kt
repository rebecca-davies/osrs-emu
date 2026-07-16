package emu.protocol.osrs239.game.codec.scene

import emu.crypto.StreamCipher
import emu.protocol.osrs239.game.message.scene.WorldEntityInfo
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.transport.codec.MessageEncoder
import emu.transport.prot.Prot

/** Encodes a zero high-resolution count and no low-resolution world entities. */
object WorldEntityInfoEncoder : MessageEncoder<WorldEntityInfo> {
    override val prot: Prot = GameServerProt.WORLD_ENTITY_INFO
    override val messageType = WorldEntityInfo::class.java
    override fun encode(cipher: StreamCipher, message: WorldEntityInfo): ByteArray = byteArrayOf(0)
}
