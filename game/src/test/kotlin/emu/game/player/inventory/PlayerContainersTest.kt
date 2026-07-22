package emu.game.player.inventory

import emu.game.obj.Obj
import emu.game.obj.ObjType
import emu.game.obj.Wearpos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerContainersTest {
    @Test
    fun `worn inventory keeps two-handed weapon and shield slots exclusive`() {
        val shield = ObjType(1, "Shield", stackable = false, wearpos = Wearpos.LEFT_HAND)
        val twoHanded =
            ObjType(
                2,
                "Two-handed weapon",
                stackable = false,
                wearpos = Wearpos.RIGHT_HAND,
                wearpos2 = Wearpos.LEFT_HAND,
            )
        val worn = PlayerWorn()

        worn.equip(shield)
        assertEquals(listOf(Obj(shield.id)), worn.equip(twoHanded))
        assertNull(worn[Wearpos.LEFT_HAND])
        assertEquals(twoHanded.id, worn[Wearpos.RIGHT_HAND]?.obj?.type)

        assertEquals(listOf(Obj(twoHanded.id)), worn.equip(shield))
        assertNull(worn[Wearpos.RIGHT_HAND])
        assertEquals(shield.id, worn[Wearpos.LEFT_HAND]?.obj?.type)
    }

    @Test
    fun `backpack snapshots remain stable after later additions`() {
        val arrows = ObjType(3, "Arrows", stackable = true)
        val inventory = PlayerInventory()
        val empty = inventory.loginSync()

        inventory.add(arrows, 100)

        assertEquals(List(PlayerInventory.CAPACITY) { null }, empty)
        assertEquals(Obj(arrows.id, 100), inventory.loginSync().first())
    }
}
