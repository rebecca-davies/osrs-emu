package emu.gateway.map

import emu.cache.def.ObjectDefinition
import emu.cache.map.MapLocDecoder
import emu.cache.map.MapSquare
import emu.cache.map.MapTileDecoder
import emu.game.pathfinding.CollisionFlag
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CacheCollisionMapBuilderTest {
    @Test
    fun `builds terrain wall rotated loc and ground decoration collision`() {
        val square = MapSquare(
            squareX = 50,
            squareY = 50,
            tiles = MapTileDecoder.decode(terrainBytes()),
            locs = MapLocDecoder.decode(locBytes()),
        )
        val definitions = mapOf(
            100 to ObjectDefinition(id = 100),
            101 to ObjectDefinition(id = 101, sizeX = 2, sizeY = 1),
            102 to ObjectDefinition(id = 102, interactType = 1),
            103 to ObjectDefinition(id = 103, interactType = 0),
        )

        val collision = CacheCollisionMapBuilder.build(listOf(square), definitions::get)

        assertEquals(0, collision.flagsAt(3_200, 3_200, 0))
        assertEquals(-1, collision.flagsAt(3_199, 3_200, 0))
        assertTrue(collision.flagsAt(3_205, 3_205, 0) and CollisionFlag.FLOOR != 0)
        assertTrue(collision.flagsAt(3_210, 3_210, 0) and CollisionFlag.WALL_WEST != 0)
        assertTrue(collision.flagsAt(3_209, 3_210, 0) and CollisionFlag.WALL_EAST != 0)
        assertTrue(collision.flagsAt(3_220, 3_220, 0) and CollisionFlag.OBJECT != 0)
        assertTrue(collision.flagsAt(3_220, 3_221, 0) and CollisionFlag.OBJECT != 0)
        assertEquals(0, collision.flagsAt(3_221, 3_220, 0) and CollisionFlag.OBJECT)
        assertTrue(collision.flagsAt(3_230, 3_230, 0) and CollisionFlag.FLOOR_DECORATION != 0)
        assertEquals(0, collision.flagsAt(3_231, 3_231, 0) and CollisionFlag.OBJECT)
    }

    private fun terrainBytes(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            for (plane in 0 until 4) {
                for (x in 0 until 64) {
                    for (y in 0 until 64) {
                        if (plane == 1 && x == 5 && y == 5) {
                            out.writeShort(52)
                        }
                        out.writeShort(0)
                    }
                }
            }
            out.writeByte(0)
        }
        return bytes.toByteArray()
    }

    private fun locBytes(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.loc(100, packedTile(10, 10), shape = 0, rotation = 0, previousId = -1)
            out.loc(101, packedTile(20, 20), shape = 10, rotation = 1, previousId = 100)
            out.loc(102, packedTile(30, 30), shape = 22, rotation = 0, previousId = 101)
            out.loc(103, packedTile(31, 31), shape = 10, rotation = 0, previousId = 102)
            out.writeShortSmart(0)
        }
        return bytes.toByteArray()
    }

    private fun DataOutputStream.loc(
        id: Int,
        packedTile: Int,
        shape: Int,
        rotation: Int,
        previousId: Int,
    ) {
        writeShortSmart(id - previousId)
        writeShortSmart(packedTile + 1)
        writeByte((shape shl 2) or rotation)
        writeShortSmart(0)
    }

    private fun DataOutputStream.writeShortSmart(value: Int) {
        if (value < 128) writeByte(value) else writeShort(value + 32_768)
    }

    private fun packedTile(localX: Int, localY: Int, plane: Int = 0): Int =
        (plane shl 12) or (localX shl 6) or localY
}
