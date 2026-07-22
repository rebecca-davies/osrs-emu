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
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InfernoArenaTest {
    @Test
    fun `simulation placement is private authoritative bounded and atomic`() {
        val type = NpcType(1, "Jal-Nib", size = 2)
        val types = NpcCatalog { id -> type.takeIf { it.id == id } }
        val blocked = Tile(12, 12)
        val map =
            GameMap(
                CollisionMap { x, y, plane ->
                    if (x == blocked.x && y == blocked.y && plane == blocked.plane) CollisionFlag.OBJECT else 0
                },
            )
        val npcs = NpcList(capacity = 2)
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

        assertEquals(InfernoNpcSelection.NotInArena, arena.selectNpc(player, type.id))
        arena.enter(player)
        assertEquals(MapInstance.privateTo(player.id), player.mapInstance)
        val selection = assertIs<InfernoNpcSelection.Selected>(arena.selectNpc(player, type.id))
        assertEquals(InfernoNpcPlacement.OCCUPIED, arena.place(player, selection, Tile(10, 10)))
        assertEquals(InfernoNpcPlacement.BLOCKED, arena.place(player, selection, blocked))
        assertEquals(InfernoNpcPlacement.OUTSIDE_ARENA, arena.place(player, selection, Tile(15, 15)))
        assertEquals(InfernoNpcSelection.UnknownType, arena.selectNpc(player, 2))
        assertEquals(InfernoPauseResult.RESUMED, arena.togglePaused(player))
        assertEquals(InfernoNpcPlacement.PLACED, arena.place(player, selection, Tile(14, 10)))
        assertEquals(InfernoPauseResult.RESUMED, arena.togglePaused(player))

        val worldBlocker = requireNotNull(npcs.add(type, Tile(20, 20), MapInstance.SHARED))
        assertEquals(InfernoNpcPlacement.WORLD_CAPACITY, arena.place(player, selection, Tile(10, 14)))
        val firstPlacement = mutableListOf<Npc>().also(npcs::collect).single { it.mapInstance == player.mapInstance }
        assertFalse(firstPlacement.paused)
        assertTrue(npcs.remove(worldBlocker))

        assertEquals(InfernoNpcPlacement.PLACED, arena.place(player, selection, Tile(10, 14)))
        val placed = mutableListOf<Npc>().also(npcs::collect)
        assertTrue(placed.all { it.paused })
        assertEquals(InfernoNpcPlacement.INSTANCE_CAPACITY, arena.place(player, selection, Tile(13, 13)))
        assertEquals(2, arena.clear(player))
        assertEquals(0, npcs.size)
        assertNotEquals(MapInstance.SHARED, player.mapInstance)
    }
}
