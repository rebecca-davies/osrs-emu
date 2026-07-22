package emu.server.game.world.cycle

import emu.compression.HuffmanCodec
import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.IncomingPlayerActionQueueConfig
import emu.game.action.PlayerAction
import emu.game.content.player.PlayerVarpCatalog
import emu.game.content.ui.config.UiComponentMap
import emu.game.content.ui.config.UiContentCatalog
import emu.game.map.GameMap
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.player.Player
import emu.game.queue.LongActionLogout
import emu.game.queue.PlayerActionPriority
import emu.game.script.content.PlayerContent
import emu.game.script.execution.PlayerScriptRequest
import emu.game.script.execution.PlayerScriptRunner
import emu.game.script.queue.PlayerQueueType
import emu.game.script.trigger.PlayerScriptRepository
import emu.game.script.trigger.ServerTriggerType
import emu.game.ui.ButtonClick
import emu.game.ui.Component
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord
import emu.persistence.character.write.CharacterWriteQueue
import emu.persistence.character.write.DurableCharacterWrite
import emu.persistence.chat.ChatAuditSink
import emu.protocol.osrs239.game.message.component.IfOpenSub
import emu.protocol.osrs239.game.message.component.IfOpenSub.Companion.MODAL
import emu.protocol.osrs239.game.message.varp.VarpSmall
import emu.server.game.network.output.GameOutputBatch
import emu.server.game.network.output.GameOutputSegment
import emu.server.game.network.output.GameOutputSink
import emu.server.game.network.output.PlayerOutput
import emu.server.game.runtime.command.WorldCommandQueue
import emu.server.game.world.World
import emu.server.game.world.activateTestPlayer
import emu.server.game.world.addTestPlayer
import emu.server.game.world.player.PlayerLifecycle
import emu.server.game.world.player.action.PlayerActions
import emu.server.game.world.player.command.PlayerCommandRepositoryBuilder
import emu.server.game.world.testWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerLogoutCycleTest {
    @Test
    fun `one failing login requests logout while later players still activate`() {
        var loginAttempts = 0
        val scripts =
            scripts {
                onLogin {
                    loginAttempts++
                    if (loginAttempts == 1) error("broken login content")
                }
            }
        val runner = PlayerScriptRunner(scripts)
        val world = testWorld(maxPlayerIndex = 2)
        val cycle = cycle(world, runner)
        val failed =
            world.addTestPlayer(
                CharacterRecord(1, "Player1", CharacterPosition(3200, 3200, 0), 0),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink { true },
            )
        val healthy =
            world.addTestPlayer(
                CharacterRecord(2, "Player2", CharacterPosition(3210, 3200, 0), 0),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink { true },
            )
        world.requestActivation(world.session(failed).token)
        world.requestActivation(world.session(healthy).token)

        cycle.tick(worldTick = 0)

        assertEquals(2, loginAttempts)
        assertFalse(failed.active)
        assertTrue(failed.logoutRequested)
        assertTrue(healthy.active)
        cycle.tick(worldTick = 1)
        assertFalse(world.contains(failed.id))
        assertTrue(world.contains(healthy.id))
    }

    @Test
    fun `global login changes survive activation and reach the first cycle output`() {
        val calls = mutableListOf<String>()
        val batches = mutableListOf<GameOutputBatch>()
        val scripts =
            scripts {
                onLogin {
                    assertFalse(player.active)
                    player.varps[PlayerVarpCatalog.RUN_MODE] = 1
                    player.interfaces.openModal(Component.of(161, 500), 200)
                    calls += "login"
                }
                onLogout { calls += "logout" }
            }
        val runner = PlayerScriptRunner(scripts)
        val world = testWorld(maxPlayerIndex = 1)
        val cycle = cycle(world, runner)
        val player =
            world.addTestPlayer(
                CharacterRecord(1, "Player1", CharacterPosition(3200, 3200, 0), 0),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink { batch -> batches += batch; true },
            )
        world.requestActivation(world.session(player).token)

        cycle.tick(worldTick = 0)
        assertEquals(listOf("login"), calls)
        assertTrue(player.active)
        assertEquals(1, player.varps[PlayerVarpCatalog.RUN_MODE])
        assertEquals(200, player.interfaces.subInterfaceAt(Component.of(161, 500)))
        val loginChanges =
            batches.single().segments
                .filterIsInstance<GameOutputSegment.Packets>()
                .flatMap(GameOutputSegment.Packets::messages)
        assertTrue(VarpSmall(PlayerVarpCatalog.RUN_MODE.id, 1) in loginChanges)
        assertTrue(IfOpenSub(161, 500, 200, MODAL) in loginChanges)

        player.requestLogout()
        cycle.tick(worldTick = 1)

        assertEquals(listOf("login", "logout"), calls)
        assertFalse(world.contains(player.id))
    }

    @Test
    fun `logout close clears old weak work before close content queues new work`() {
        val calls = mutableListOf<String>()
        val closeQueue = PlayerQueueType.unit("close_queue")
        val components =
            UiComponentMap.parse(
                "[components]\n\"test:modal\" = 13107200",
            )
        val scripts =
            PlayerScriptRepository.build(components) {
                onQueue(closeQueue) { calls += "weak" }
                onClose("test:modal") {
                    calls += "close"
                    weakQueue(closeQueue)
                }
            }
        val runner = PlayerScriptRunner(scripts)
        val world = testWorld(maxPlayerIndex = 1)
        val cycle = cycle(world, runner)
        val player = player(world)
        player.interfaces.openModal(Component.of(161, 500), 200)
        player.actionQueue.add(
            PlayerScriptRequest(scripts.require(closeQueue)),
            PlayerActionPriority.WEAK,
            delayTicks = 10,
        )
        player.requestLogout()

        cycle.tick(worldTick = 0)

        assertEquals(listOf("close"), calls)
        assertEquals(1, player.actionQueue.weakSize)
    }

    @Test
    fun `logout trigger suspension is discarded before snapshot and removal`() {
        val calls = mutableListOf<String>()
        val scripts =
            scripts {
                onLogout {
                    calls += "start"
                    delay(2)
                    calls += "finish"
                }
            }
        val runner = PlayerScriptRunner(scripts)
        val world = testWorld(maxPlayerIndex = 1)
        val cycle = cycle(world, runner)
        val player = player(world)
        player.requestLogout()

        cycle.tick(worldTick = 0)
        assertEquals(listOf("start"), calls)
        assertFalse(world.contains(player.id))
    }

    @Test
    fun `disconnect abandons suspended client input before durable removal`() {
        val ui = UiContentCatalog.load()
        val scripts =
            PlayerScriptRepository.build(ui) {
                onButton("stats:attack") { numberDialog("Set level:") }
            }
        val runner = PlayerScriptRunner(scripts)
        val world = testWorld(maxPlayerIndex = 1)
        val cycle = cycle(world, runner)
        val player = player(world)
        val component = ui.components.require("stats:attack")
        runner.trigger(
            player,
            ServerTriggerType.IF_BUTTON,
            component.packed,
            ButtonClick(component.interfaceId, component.componentId),
        )
        assertFalse(player.canAccess())

        world.disconnect(world.session(player).token)
        cycle.tick(worldTick = 0)

        assertFalse(world.contains(player.id))
    }

    @Test
    fun `throwing logout content runs once and write back removes the player next cycle`() {
        var logoutAttempts = 0
        val scripts =
            scripts {
                onLogout {
                    logoutAttempts++
                    error("broken logout content")
                }
            }
        val runner = PlayerScriptRunner(scripts)
        val world = testWorld(maxPlayerIndex = 1)
        val cycle = cycle(world, runner)
        val player = player(world)
        player.requestLogout()

        cycle.tick(worldTick = 0)

        assertEquals(1, logoutAttempts)
        assertTrue(player.loggingOut)
        assertTrue(world.contains(player.id))
        cycle.tick(worldTick = 1)
        assertEquals(1, logoutAttempts)
        assertFalse(world.contains(player.id))
    }

    @Test
    fun `logout request transitions after player processing and accelerates long work next cycle`() {
        val calls = mutableListOf<String>()
        val queueType = PlayerQueueType.unit("logout_test")
        val scripts = scripts { onQueue(queueType) { calls += "long" } }
        val runner = PlayerScriptRunner(scripts)
        val world = testWorld(maxPlayerIndex = 1)
        val cycle = cycle(world, runner)
        val player = player(world)
        player.actionQueue.addLong(
            PlayerScriptRequest(scripts.require(queueType)),
            delayTicks = 10,
            logout = LongActionLogout.ACCELERATE,
        )
        player.requestLogout()

        cycle.tick(worldTick = 0)

        assertTrue(player.loggingOut)
        assertTrue(world.contains(player.id))
        assertEquals(emptyList(), calls)

        cycle.tick(worldTick = 1)

        assertEquals(listOf("long"), calls)
        assertFalse(world.contains(player.id))
    }

    @Test
    fun `idle action enters the normal logout trigger and durable write back lifecycle`() {
        val calls = mutableListOf<String>()
        val scripts = scripts { onLogout { calls += "logout" } }
        val runner = PlayerScriptRunner(scripts)
        val world = testWorld(maxPlayerIndex = 1)
        val cycle = cycle(world, runner)
        val player =
            world.addTestPlayer(
                CharacterRecord(1, "Player1", CharacterPosition(3200, 3200, 0), 0),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink { true },
            )
        world.activateTestPlayer(world.session(player).token)
        world.session(player).actions.submit(PlayerAction.IdleLogout)

        cycle.tick(worldTick = 0)

        assertEquals(listOf("logout"), calls)
        assertTrue(player.loggingOut)
        assertFalse(player.idleLogoutRequested)
        assertFalse(world.contains(player.id))
    }

    @Test
    fun `shutdown bypasses protection and abandons delayed scripts before write back`() {
        val calls = mutableListOf<String>()
        val queueType = PlayerQueueType.unit("shutdown_test")
        val scripts =
            scripts {
                onQueue(queueType) {
                    calls += "start"
                    delay(3)
                    calls += "finish"
                }
            }
        val runner = PlayerScriptRunner(scripts)
        val world = testWorld(maxPlayerIndex = 1)
        val cycle = cycle(world, runner)
        val player = player(world)
        player.actionQueue.add(PlayerScriptRequest(scripts.require(queueType)))
        cycle.tick(worldTick = 0)
        assertEquals(listOf("start"), calls)
        assertFalse(player.canAccess())

        cycle.beginShutdown()
        assertTrue(player.canAccess())
        val finished = cycle.shutdownStep()

        assertTrue(finished)
        assertEquals(listOf("start"), calls)
        assertFalse(world.contains(player.id))
    }

    private fun player(world: World): Player {
        val player =
            world.addTestPlayer(
                CharacterRecord(1, "Player1", CharacterPosition(3200, 3200, 0), 0),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink { true },
            )
        world.activateTestPlayer(world.session(player).token)
        return player
    }

    private fun scripts(content: PlayerContent.() -> Unit): PlayerScriptRepository =
        PlayerScriptRepository.build(UiComponentMap.parse("[components]"), content)

    private fun cycle(
        world: World,
        runner: PlayerScriptRunner,
    ): WorldCycle {
        return WorldCycle(
            world,
            WorldCommandQueue(capacity = 8),
            PlayerActions(
                GameMap(OpenCollisionMap),
                runner,
                PlayerCommandRepositoryBuilder().build(),
                ChatAuditSink { true },
            ),
            PlayerPhase(runner),
            PlayerLifecycle(world, CharacterWriteQueue { DurableCharacterWrite }, runner),
            PlayerOutput(world, HuffmanCodec(ByteArray(256) { 8 })),
        )
    }
}
