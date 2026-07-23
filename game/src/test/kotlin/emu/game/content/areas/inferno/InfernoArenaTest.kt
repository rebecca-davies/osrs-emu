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
                exitPortalType = 2,
                clanWarsArrival = Tile(5, 5),
                arenaArrival = Tile(10, 10),
                arenaBounds = InfernoArenaBounds(Tile(10, 10), Tile(15, 15)),
                maxNpcs = 2,
                editorRoster = InfernoEditorRoster(listOf(InfernoEditorNpc(type.id, type.name))),
            )
        val arena = InfernoArena(map, types, npcs, config)
        val player = testPlayer(Tile(5, 5))

        assertEquals(InfernoNpcSelection.NotInArena, arena.selectNpcAt(player, 0))
        assertEquals(type.id, arena.enter(player).selectedNpc.type)
        assertEquals(MapInstance.privateTo(player.id), player.mapInstance)
        assertEquals(type, assertIs<InfernoNpcSelection.Selected>(arena.selectNpcAt(player, 0)).type)
        assertEquals(InfernoNpcPlacement.OCCUPIED, arena.placeSelected(player, Tile(10, 10)))
        assertEquals(InfernoNpcPlacement.BLOCKED, arena.placeSelected(player, blocked))
        assertEquals(InfernoNpcPlacement.OUTSIDE_ARENA, arena.placeSelected(player, Tile(15, 15)))
        assertEquals(InfernoNpcSelection.UnknownType, arena.selectNpcAt(player, 1))
        assertEquals(InfernoPauseResult.RESUMED, arena.togglePaused(player))
        assertEquals(InfernoNpcPlacement.PLACED, arena.placeSelected(player, Tile(14, 10)))
        assertEquals(InfernoPauseResult.RESUMED, arena.togglePaused(player))

        val worldBlocker = requireNotNull(npcs.add(type, Tile(20, 20), MapInstance.SHARED))
        assertEquals(InfernoNpcPlacement.WORLD_CAPACITY, arena.placeSelected(player, Tile(10, 14)))
        val firstPlacement = mutableListOf<Npc>().also(npcs::collect).single { it.mapInstance == player.mapInstance }
        assertFalse(firstPlacement.paused)
        assertTrue(npcs.remove(worldBlocker))

        assertEquals(InfernoNpcPlacement.PLACED, arena.placeSelected(player, Tile(10, 14)))
        val placed = mutableListOf<Npc>().also(npcs::collect)
        assertTrue(placed.all { it.paused })
        assertEquals(InfernoNpcPlacement.INSTANCE_CAPACITY, arena.placeSelected(player, Tile(13, 13)))
        assertEquals(2, arena.clear(player))
        assertEquals(0, npcs.size)
        assertNotEquals(MapInstance.SHARED, player.mapInstance)
        assertTrue(arena.reset(player) != null)
        assertEquals(config.arenaArrival, player.movement.position)
        assertEquals(0, arena.release(player))
        assertFalse(arena.isActive(player))
    }
}
