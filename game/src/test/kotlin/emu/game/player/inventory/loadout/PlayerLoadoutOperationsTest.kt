package emu.game.player.inventory.loadout

import emu.game.map.Tile
import emu.game.obj.Obj
import emu.game.obj.ObjStack
import emu.game.obj.ObjType
import emu.game.obj.Wearpos
import emu.game.player.testPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PlayerLoadoutOperationsTest {
    @Test
    fun `loadout preflight rejects aggregate stack overflow`() {
        val arrows = ObjType(1, "Arrows", stackable = true)

        val loadout =
            PlayerLoadout.create(
                "Overflow",
                worn = emptyList(),
                inventory = listOf(ObjStack(arrows, Int.MAX_VALUE), ObjStack(arrows)),
            )

        assertNull(loadout)
    }

    @Test
    fun `provision rejects stack overflow without changing the backpack`() {
        val arrows = ObjType(1, "Arrows", stackable = true)
        val player = testPlayer(Tile(3_200, 3_200))
        player.inventory.add(arrows, Int.MAX_VALUE)
        player.inventory.drainClientUpdate()

        assertEquals(PlayerProvisionResult.NO_SPACE, player.provision(arrows))
        assertEquals(Obj(arrows.id, Int.MAX_VALUE), player.inventory.loginSync().first())
        assertNull(player.inventory.drainClientUpdate())
    }

    @Test
    fun `equipping preserves both containers when displaced equipment cannot fit`() {
        val filler = ObjType(1, "Filler", stackable = false)
        val shield = ObjType(2, "Shield", stackable = false, wearpos = Wearpos.LEFT_HAND)
        val twoHanded =
            ObjType(
                3,
                "Two-handed weapon",
                stackable = false,
                wearpos = Wearpos.RIGHT_HAND,
                wearpos2 = Wearpos.LEFT_HAND,
            )
        val player = testPlayer(Tile(3_200, 3_200))
        player.inventory.add(filler, 28)
        player.worn.equip(shield)
        player.inventory.drainClientUpdate()
        player.worn.drainClientUpdate()

        assertEquals(PlayerProvisionResult.NO_SPACE, player.provisionWorn(twoHanded))
        assertEquals(shield.id, player.worn[Wearpos.LEFT_HAND]?.obj?.type)
        assertNull(player.worn[Wearpos.RIGHT_HAND])
        assertEquals(List(28) { Obj(filler.id) }, player.inventory.loginSync())
        assertNull(player.inventory.drainClientUpdate())
        assertNull(player.worn.drainClientUpdate())
    }

    @Test
    fun `preflighted loadout atomically replaces worn and backpack state`() {
        val arrows = ObjType(1, "Arrows", stackable = true)
        val bow = ObjType(2, "Bow", stackable = false, wearpos = Wearpos.RIGHT_HAND)
        val filler = ObjType(3, "Filler", stackable = false)
        val player = testPlayer(Tile(3_200, 3_200))
        player.inventory.add(filler)
        val loadout =
            requireNotNull(
                PlayerLoadout.create(
                    "Ranged",
                    worn = listOf(ObjStack(bow)),
                    inventory = listOf(ObjStack(arrows, 100)),
                ),
            )

        player.applyLoadout(loadout)

        assertEquals(Obj(arrows.id, 100), player.inventory.loginSync().first())
        assertEquals(bow.id, player.worn[Wearpos.RIGHT_HAND]?.obj?.type)
        assertFalse(player.inventory.loginSync().any { it?.type == filler.id })
    }
}
