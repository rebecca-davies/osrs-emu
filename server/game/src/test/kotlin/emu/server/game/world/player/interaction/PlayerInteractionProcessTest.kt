package emu.server.game.world.player.interaction

import emu.game.content.ui.config.UiComponentMap
import emu.game.loc.Loc
import emu.game.loc.LocRepository
import emu.game.map.GameMap
import emu.game.map.MapInstance
import emu.game.map.Tile
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.player.interaction.PlayerInteraction
import emu.game.script.content.PlayerContent
import emu.game.script.execution.PlayerScriptRunner
import emu.game.script.trigger.PlayerScriptRepository
import emu.server.game.testPlayer
import emu.server.game.testNpcTargets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PlayerInteractionProcessTest {
    @Test
    fun `loc removed during approach cannot trigger stale content`() {
        val loc =
            Loc(
                type = 1,
                tile = Tile(10, 10),
                shape = 10,
                angle = 0,
                width = 1,
                length = 1,
                options = setOf(1),
            )
        var present = true
        val map =
            GameMap(
                OpenCollisionMap,
                LocRepository { type, tile ->
                    loc.takeIf { present && it.type == type && it.tile == tile }
                },
            )
        var triggered = false
        val runner = runner { onLoc1(loc.type) { triggered = true } }
        val player = testPlayer(Tile(5, 10))
        player.beginInteraction(
            PlayerInteraction.LocOp(
                target = loc,
                option = 1,
                subOption = 0,
                mapInstance = player.mapInstance,
            ),
        )
        player.pathTo(loc)
        map.resolveRoute(player)
        present = false

        val interactions = PlayerInteractionProcess(map, runner, testNpcTargets())
        interactions.beforeMovement(player)

        assertFalse(triggered)
        assertNull(player.interaction)
        assertFalse(player.movement.hasRoute)
        assertEquals(Tile(5, 10), player.movement.position)
        map.advance(player)
        assertEquals(Tile(5, 10), player.movement.position)
    }

    @Test
    fun `interaction from another map instance is cancelled before movement`() {
        val loc =
            Loc(
                type = 1,
                tile = Tile(10, 10),
                shape = 10,
                angle = 0,
                width = 1,
                length = 1,
                options = setOf(1),
            )
        val map = GameMap(OpenCollisionMap, LocRepository { _, _ -> loc })
        val player = testPlayer(Tile(5, 10))
        player.beginInteraction(
            PlayerInteraction.LocOp(
                target = loc,
                option = 1,
                subOption = 0,
                mapInstance = MapInstance.privateTo(player.id),
            ),
        )
        player.pathTo(loc)
        map.resolveRoute(player)

        PlayerInteractionProcess(map, runner {}, testNpcTargets()).beforeMovement(player)

        assertNull(player.interaction)
        assertFalse(player.movement.hasRoute)
        assertEquals(Tile(5, 10), player.movement.position)
    }

    private fun runner(content: PlayerContent.() -> Unit): PlayerScriptRunner =
        PlayerScriptRunner(PlayerScriptRepository.build(UiComponentMap.parse("[components]"), content))
}
