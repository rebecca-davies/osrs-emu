package emu.server.game.world.player.interaction

import emu.game.action.PlayerAction
import emu.game.content.player.PlayerVarpCatalog
import emu.game.content.ui.config.UiComponentMap
import emu.game.map.GameMap
import emu.game.map.MapInstance
import emu.game.map.Tile
import emu.game.npc.Npc
import emu.game.npc.NpcCatalog
import emu.game.npc.NpcConditionalOperation
import emu.game.npc.NpcList
import emu.game.npc.NpcOpInput
import emu.game.npc.NpcOpTarget
import emu.game.npc.NpcOperations
import emu.game.npc.NpcTransform
import emu.game.npc.NpcType
import emu.game.npc.NpcUid
import emu.game.pathfinding.collision.CollisionMap
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.pathfinding.movement.MovementUpdate
import emu.game.player.Player
import emu.game.player.interaction.PlayerInteraction
import emu.game.script.content.PlayerContent
import emu.game.script.execution.PlayerScriptRunner
import emu.game.script.trigger.PlayerScriptRepository
import emu.game.varp.PlayerVariable
import emu.persistence.chat.ChatAuditSink
import emu.server.game.testNpcTargets
import emu.server.game.testPlayer
import emu.server.game.world.player.action.PlayerActions
import emu.server.game.world.player.command.PlayerCommandRepositoryBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NpcInteractionProcessTest {
    @Test
    fun `NPC operation waits for the player phase and carries its stable exact target`() {
        val type =
            NpcType(
                7_691,
                "Jal-Nib",
                operations = NpcOperations(subOptions = mapOf(2 to setOf(7))),
            )
        var received: NpcOpTarget? = null
        val runtime =
            runtime(type, npcPosition = Tile(11, 10)) {
                onNpc2(type.id) { received = it }
            }

        runtime.click(NpcOpInput(runtime.npc.index, option = 2, subOption = 7))

        assertNull(received)
        assertIs<PlayerInteraction.NpcOp>(runtime.player.interaction)

        runtime.cycle()

        assertEquals(NpcOpTarget(NpcUid(runtime.npc.index, runtime.npc.uid), 7), received)
        assertNull(runtime.player.interaction)

        runtime.player.teleportTo(runtime.player.movement.position, MapInstance.privateTo(runtime.player.id))
        runtime.click(NpcOpInput(runtime.npc.index, option = 2, subOption = 7))
        assertNull(runtime.player.interaction)
    }

    @Test
    fun `NPC operation reroutes to a moved target and preserves control-run inversion`() {
        val type = NpcType(1, "Jal-MejRah", operations = NpcOperations(options = setOf(2)))
        var received: NpcOpTarget? = null
        val runtime =
            runtime(type, playerPosition = Tile(10, 10), npcPosition = Tile(15, 10)) {
                onNpc2(type.id) { received = it }
            }

        runtime.click(NpcOpInput(runtime.npc.index, option = 2, controlKey = true))
        runtime.npc.walkTo(Tile(15, 11))
        runtime.cycle()

        assertIs<MovementUpdate.Run>(runtime.player.movement.update)
        assertTrue(runtime.player.movement.isRoutedTo(runtime.npc.position, type.size))

        repeat(8) { runtime.cycle() }

        assertEquals(NpcUid(runtime.npc.index, runtime.npc.uid), received?.uid)
        assertNull(runtime.player.interaction)
        assertFalse(runtime.player.movement.runEnabled)
    }

    @Test
    fun `removed NPC cannot retarget a replacement that reuses its client slot`() {
        val type = NpcType(1, "Jal-Nib", operations = NpcOperations(options = setOf(2)))
        var triggers = 0
        val runtime =
            runtime(type, playerPosition = Tile(10, 10), npcPosition = Tile(15, 10)) {
                onNpc2(type.id) { triggers++ }
            }
        runtime.click(NpcOpInput(runtime.npc.index, option = 2))
        val originalUid = runtime.npc.uid

        assertTrue(runtime.npcs.remove(runtime.npc))
        val replacement =
            assertNotNull(
                runtime.npcs.add(type, Tile(15, 10), MapInstance.SHARED),
            )
        assertEquals(runtime.npc.index, replacement.index)
        assertNotEquals(originalUid, replacement.uid)

        runtime.interactions.beforeMovement(runtime.player)

        assertEquals(0, triggers)
        assertNull(runtime.player.interaction)
        assertFalse(runtime.player.movement.hasRoute)
        assertEquals(Tile(10, 10), runtime.player.movement.position)
    }

    @Test
    fun `exhausted NPC route reports unreachable once and clears the interaction`() {
        val type = NpcType(1, "Jal-Nib", operations = NpcOperations(options = setOf(2)))
        val collision = CollisionMap { x, y, _ -> if (x == 10 && y == 10) 0 else -1 }
        val runtime =
            runtime(
                type,
                playerPosition = Tile(10, 10),
                npcPosition = Tile(13, 10),
                collision = collision,
            )

        runtime.click(NpcOpInput(runtime.npc.index, option = 2))
        runtime.cycle()

        assertEquals(listOf("I can't reach that!"), runtime.player.takeGameMessages())
        assertNull(runtime.player.interaction)
        assertFalse(runtime.player.movement.hasRoute)
    }

    @Test
    fun `player-specific morph size controls reach and a later morph change cancels`() {
        val variable = PlayerVariable.Varp(PlayerVarpCatalog.RUN_MODE.id)
        val base = NpcType(1, "Morphed NPC", transform = NpcTransform(variable, listOf(2, 3)))
        val large = NpcType(2, "Large NPC", size = 3, operations = NpcOperations(options = setOf(2)))
        val changed = NpcType(3, "Changed NPC", operations = NpcOperations(options = setOf(2)))
        val types = listOf(base, large, changed).associateBy(NpcType::id)
        val triggered = mutableListOf<Int>()
        val reached =
            runtime(
                base,
                types = NpcCatalog(types::get),
                playerPosition = Tile(14, 9),
                npcPosition = Tile(13, 10),
            ) {
                onNpc2(large.id) { triggered += large.id }
            }

        reached.click(NpcOpInput(reached.npc.index, option = 2))
        reached.cycle()
        assertEquals(listOf(large.id), triggered)

        val changedDuringApproach =
            runtime(
                base,
                types = NpcCatalog(types::get),
                playerPosition = Tile(10, 10),
                npcPosition = Tile(15, 10),
            ) {
                onNpc2(large.id) { triggered += large.id }
                onNpc2(changed.id) { triggered += changed.id }
            }
        changedDuringApproach.click(NpcOpInput(changedDuringApproach.npc.index, option = 2))
        changedDuringApproach.player.varps[PlayerVarpCatalog.RUN_MODE] = 1
        changedDuringApproach.interactions.beforeMovement(changedDuringApproach.player)

        assertEquals(listOf(large.id), triggered)
        assertNull(changedDuringApproach.player.interaction)
        assertFalse(changedDuringApproach.player.movement.hasRoute)
    }

    @Test
    fun `retained NPC operation cancels when its controlling varp hides it during approach`() {
        val variable = PlayerVariable.Varp(PlayerVarpCatalog.RUN_MODE.id)
        val type =
            NpcType(
                1,
                "Conditional NPC",
                operations =
                    NpcOperations(
                        conditionalOptions =
                            mapOf(
                                2 to
                                    listOf(
                                        NpcConditionalOperation(variable, 0..0, visible = true),
                                        NpcConditionalOperation(variable, 1..1, visible = false),
                                    ),
                            ),
                    ),
            )
        var triggers = 0
        val runtime =
            runtime(
                type,
                playerPosition = Tile(10, 10),
                npcPosition = Tile(15, 10),
            ) {
                onNpc2(type.id) { triggers++ }
            }

        runtime.click(NpcOpInput(runtime.npc.index, option = 2))
        assertIs<PlayerInteraction.NpcOp>(runtime.player.interaction)
        runtime.player.varps[PlayerVarpCatalog.RUN_MODE] = 1
        runtime.cycle()

        assertEquals(0, triggers)
        assertNull(runtime.player.interaction)
        assertFalse(runtime.player.movement.hasRoute)
        assertEquals(Tile(10, 10), runtime.player.movement.position)
    }

    private fun runtime(
        type: NpcType,
        types: NpcCatalog = NpcCatalog.EMPTY,
        playerPosition: Tile = Tile(10, 10),
        npcPosition: Tile,
        collision: CollisionMap = OpenCollisionMap,
        content: PlayerContent.() -> Unit = {},
    ): Runtime {
        val map = GameMap(collision)
        val player = testPlayer(position = playerPosition)
        val npcs = NpcList(capacity = 1)
        val npc = requireNotNull(npcs.add(type, npcPosition, MapInstance.SHARED))
        val scripts =
            PlayerScriptRunner(
                PlayerScriptRepository.build(UiComponentMap.parse("[components]"), content),
            )
        val targets = testNpcTargets(npcs, types)
        return Runtime(
            map,
            player,
            npcs,
            npc,
            PlayerActions(
                map,
                targets,
                scripts,
                PlayerCommandRepositoryBuilder().build(),
                ChatAuditSink { true },
            ),
            PlayerInteractionProcess(map, scripts, targets),
        )
    }

    private data class Runtime(
        val map: GameMap,
        val player: Player,
        val npcs: NpcList,
        val npc: Npc,
        val actions: PlayerActions,
        val interactions: PlayerInteractionProcess,
    ) {
        fun click(input: NpcOpInput) {
            actions.apply(player, PlayerAction.NpcOp(input))
            map.resolveRoute(player)
        }

        fun cycle() {
            interactions.beforeMovement(player)
            map.advance(player)
            interactions.afterMovement(player)
        }
    }
}
