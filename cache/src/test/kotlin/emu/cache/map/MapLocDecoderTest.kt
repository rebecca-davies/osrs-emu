package emu.cache.map

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class MapLocDecoderTest {
    @Test
    fun `decodes delta smart loc ids coordinates shapes and rotations`() {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeIncrShortSmart(101)
            out.writeShortSmart(132)
            out.writeByte((0 shl 2) or 1)
            out.writeShortSmart(2)
            out.writeByte((2 shl 2) or 3)
            out.writeShortSmart(0)

            out.writeIncrShortSmart(32_900)
            out.writeShortSmart(12_287)
            out.writeByte((22 shl 2) or 3)
            out.writeShortSmart(0)
            out.writeIncrShortSmart(0)
        }

        assertEquals(
            listOf(
                MapLocSpawn(id = 100, localX = 2, localY = 3, plane = 0, shape = 0, rotation = 1),
                MapLocSpawn(id = 100, localX = 2, localY = 4, plane = 0, shape = 2, rotation = 3),
                MapLocSpawn(id = 33_000, localX = 63, localY = 62, plane = 2, shape = 22, rotation = 3),
            ),
            MapLocDecoder.decode(bytes.toByteArray()),
        )
    }

    private fun DataOutputStream.writeIncrShortSmart(value: Int) {
        var remaining = value
        while (remaining >= 32_767) {
            writeShortSmart(32_767)
            remaining -= 32_767
        }
        writeShortSmart(remaining)
    }

    private fun DataOutputStream.writeShortSmart(value: Int) {
        require(value in 0..32_767)
        if (value < 128) writeByte(value) else writeShort(value + 32_768)
    }
}
