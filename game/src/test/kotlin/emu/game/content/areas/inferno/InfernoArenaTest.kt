package emu.game.content.areas.inferno

import emu.game.map.GameMap
import emu.game.map.MapInstance
import emu.game.map.Tile
import emu.game.npc.Npc
import emu.game.npc.NpcCatalog
import emu.game.npc.NpcList
import emu.game.npc.NpcType
import emu.game.pathfinding.collision.CollisionFlag
import emu.game.pathfinding.collision.CollisionMap
import emu.game.player.testPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InfernoArenaTest {
    @Test
    fun `placement is private collision authoritative bounded and automatically paused`() {
        val type = NpcType(1, "Jal-Nib", size = 2)
        val types = NpcCatalog { id -> type.takeIf { it.id == id } }
        val blocked = Tile(12, 12)
        val map =
            GameMap(
                CollisionMap { x, y, plane ->
                    if (x == blocked.x && y == blocked.y && plane == blocked.plane) CollisionFlag.OBJECT else 0
                },
            )
        val npcs = NpcList(capacity = 3)
        val config =
            InfernoFreeModeConfig(
                challengePortalType = 1,
                clanWarsArrival = Tile(5, 5),
                arenaArrival = Tile(10, 10),
                arenaBounds = InfernoArenaBounds(Tile(10, 10), Tile(15, 15)),
                maxNpcs = 2,
            )
        val arena = InfernoArena(map, types, npcs, config)
        val player = testPlayer(Tile(5, 5))

        assertEquals(InfernoNpcPlacement.NOT_IN_ARENA, arena.place(player, type.id, Tile(10, 10)))
        arena.enter(player)
        assertEquals(MapInstance.privateTo(player.id), player.mapInstance)
        assertEquals(InfernoNpcPlacement.OCCUPIED, arena.place(player, type.id, Tile(10, 10)))
        assertEquals(InfernoNpcPlacement.BLOCKED, arena.place(player, type.id, blocked))
        assertEquals(InfernoNpcPlacement.OUTSIDE_ARENA, arena.place(player, type.id, Tile(15, 15)))
        assertEquals(InfernoNpcPlacement.UNKNOWN_TYPE, arena.place(player, 2, Tile(14, 10)))
        assertEquals(InfernoNpcPlacement.PLACED, arena.place(player, type.id, Tile(14, 10)))
        assertEquals(false, arena.togglePaused(player))

        assertEquals(InfernoNpcPlacement.PLACED, arena.place(player, type.id, Tile(10, 14)))
        val placed = mutableListOf<Npc>().also(npcs::collect)
        assertTrue(placed.all { it.paused })
        assertEquals(InfernoNpcPlacement.INSTANCE_CAPACITY, arena.place(player, type.id, Tile(13, 13)))
        assertEquals(2, arena.clear(player))
        assertEquals(0, npcs.size)
        assertNotEquals(MapInstance.SHARED, player.mapInstance)
    }
}
