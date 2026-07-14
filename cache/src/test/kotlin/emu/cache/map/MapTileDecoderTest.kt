package emu.cache.map

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class MapTileDecoderTest {
    @Test
    fun `decodes movement bridge and roof rules from rev 239 terrain tiles`() {
        val encoded = terrainBytes { x, y, plane, out ->
            when (Triple(x, y, plane)) {
                Triple(0, 0, 0) -> out.opcodes(50, 0)
                Triple(0, 1, 0) -> out.opcodes(51, 0)
                Triple(0, 2, 0) -> out.opcodes(53, 0)
                Triple(0, 3, 0) -> out.opcodes(50, 51, 0)
                Triple(0, 4, 0) -> {
                    out.writeShort(2)
                    out.writeShort(123)
                    out.opcodes(50, 0)
                }
                else -> out.writeShort(0)
            }
        }

        val flags = MapTileDecoder.decode(encoded)

        assertEquals(MapTileFlags.BLOCK_MOVEMENT, flags[0, 0, 0])
        assertEquals(MapTileFlags.LINK_BELOW, flags[0, 1, 0])
        assertEquals(MapTileFlags.REMOVE_ROOFS, flags[0, 2, 0])
        assertEquals(MapTileFlags.BLOCK_MOVEMENT or MapTileFlags.LINK_BELOW, flags[0, 3, 0])
        assertEquals(MapTileFlags.BLOCK_MOVEMENT, flags[0, 4, 0])
        assertEquals(0, flags[63, 63, 3])
    }

    private fun terrainBytes(writeTile: (Int, Int, Int, DataOutputStream) -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            for (plane in 0 until 4) {
                for (x in 0 until 64) {
                    for (y in 0 until 64) writeTile(x, y, plane, out)
                }
            }
            out.writeByte(0)
        }
        return bytes.toByteArray()
    }

    private fun DataOutputStream.opcodes(vararg values: Int) {
        for (value in values) writeShort(value)
    }
}
