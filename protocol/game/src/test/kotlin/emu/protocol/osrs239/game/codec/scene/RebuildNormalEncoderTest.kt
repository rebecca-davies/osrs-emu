package emu.protocol.osrs239.game.codec.scene

import emu.crypto.NopStreamCipher
import emu.protocol.osrs239.game.message.scene.RebuildNormal
import emu.protocol.osrs239.game.prot.GameServerProt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class RebuildNormalEncoderTest {
    @Test
    fun `in game rebuild binds to normal rebuild prot`() {
        assertEquals(GameServerProt.REBUILD_NORMAL, RebuildNormalEncoder.prot)
        assertEquals(RebuildNormal::class.java, RebuildNormalEncoder.messageType)
    }

    @Test
    fun `in game rebuild contains only hint and transformed centre zones`() {
        val body = RebuildNormalEncoder.encode(
            NopStreamCipher,
            RebuildNormal(centreZoneX = 407, centreZoneY = 402),
        )

        assertContentEquals(
            byteArrayOf(0, 0, 1, 18, 1, 23),
            body,
        )
    }
}
