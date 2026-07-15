package emu.server.world.runtime

import emu.server.world.cycle.WorldCycle
import emu.server.world.entity.WorldPlayer
import emu.compression.HuffmanCodec
import emu.game.action.GameInputQueue
import emu.game.action.GameInputQueueConfig
import emu.game.content.ui.UiComponentMap
import emu.game.content.player.PlayerVarpCatalog
import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PlayerMovementProcess
import emu.game.queue.LongActionLogout
import emu.game.script.PlayerQueueType
import emu.game.script.PlayerContent
import emu.game.script.PlayerScriptRepository
import emu.game.script.PlayerScriptRequest
import emu.game.script.PlayerScriptRunner
import emu.game.ui.Component
import emu.persistence.character.DurableCharacterWrite
import emu.persistence.character.CharacterWriteQueue
import emu.persistence.character.PlayerPosition
import emu.persistence.character.PlayerRecord
import emu.persistence.chat.ChatAuditSink
import emu.server.world.TestPlayerContent
import emu.server.world.network.GameOutputBatch
import emu.server.world.network.GameOutputSegment
import emu.server.world.network.GameOutputSink
import emu.server.world.player.PlayerActionProcess
import emu.server.world.player.PlayerChatActionProcess
import emu.server.world.player.PlayerLifecycleProcess
import emu.server.world.player.PlayerOutputProcess
import emu.server.world.player.PlayerScriptProcess
import emu.server.world.player.PlayerTriggerProcess
import emu.server.world.player.RouteSearchBudget
import emu.server.world.config.RouteSearchConfig
import emu.protocol.osrs239.game.message.IfOpenSub
import emu.protocol.osrs239.game.message.IfOpenSub.Companion.MODAL
import emu.protocol.osrs239.game.message.VarpSmall
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
        val world = testGameWorld(maxPlayerIndex = 2)
        val cycle = cycle(world, scripts, runner)
        val failed =
            world.addTestPlayer(
                PlayerRecord(1, "Player1", PlayerPosition(3200, 3200, 0), 0),
                GameInputQueue(GameInputQueueConfig()),
                GameOutputSink { true },
            )
        val healthy =
            world.addTestPlayer(
                PlayerRecord(2, "Player2", PlayerPosition(3210, 3200, 0), 0),
                GameInputQueue(GameInputQueueConfig()),
                GameOutputSink { true },
            )
        world.requestActivation(failed.connection.token)
        world.requestActivation(healthy.connection.token)

        cycle.tick(worldTick = 0)

        assertEquals(2, loginAttempts)
        assertFalse(failed.player.active)
        assertTrue(failed.player.logoutRequested)
        assertTrue(healthy.player.active)
        cycle.tick(worldTick = 1)
        assertFalse(world.contains(failed.player.id))
        assertTrue(world.contains(healthy.player.id))
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
        val world = testGameWorld(maxPlayerIndex = 1)
        val cycle = cycle(world, scripts, runner)
        val connected =
            world.addTestPlayer(
                PlayerRecord(1, "Player1", PlayerPosition(3200, 3200, 0), 0),
                GameInputQueue(GameInputQueueConfig()),
                GameOutputSink { batch -> batches += batch; true },
            )
        world.requestActivation(connected.connection.token)

        cycle.tick(worldTick = 0)
        assertEquals(listOf("login"), calls)
        assertTrue(connected.player.active)
        assertEquals(1, connected.player.varps[PlayerVarpCatalog.RUN_MODE])
        assertEquals(200, connected.player.interfaces.subInterfaceAt(Component.of(161, 500)))
        val loginChanges =
            batches.single().segments
                .filterIsInstance<GameOutputSegment.Packets>()
                .flatMap(GameOutputSegment.Packets::messages)
        assertTrue(VarpSmall(PlayerVarpCatalog.RUN_MODE.id, 1) in loginChanges)
        assertTrue(IfOpenSub(161, 500, 200, MODAL) in loginChanges)

        connected.player.requestLogout()
        cycle.tick(worldTick = 1)

        assertEquals(listOf("login", "logout"), calls)
        assertFalse(world.contains(connected.player.id))
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
        val world = testGameWorld(maxPlayerIndex = 1)
        val cycle = cycle(world, scripts, runner)
        val player = player(world)
        player.requestLogout()

        cycle.tick(worldTick = 0)
        assertEquals(listOf("start"), calls)
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
        val world = testGameWorld(maxPlayerIndex = 1)
        val cycle = cycle(world, scripts, runner)
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
        val world = testGameWorld(maxPlayerIndex = 1)
        val cycle = cycle(world, scripts, runner)
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
        val world = testGameWorld(maxPlayerIndex = 1)
        val cycle = cycle(world, scripts, runner)
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

    private fun player(world: GameWorld): WorldPlayer {
        val connected =
            world.addTestPlayer(
                PlayerRecord(1, "Player1", PlayerPosition(3200, 3200, 0), 0),
                GameInputQueue(GameInputQueueConfig()),
                GameOutputSink { true },
            )
        world.activateTestPlayer(connected.connection.token)
        return connected.player
    }

    private fun scripts(content: PlayerContent.() -> Unit): PlayerScriptRepository =
        PlayerScriptRepository.build(UiComponentMap.parse("[components]"), content)

    private fun cycle(
        world: GameWorld,
        scripts: PlayerScriptRepository,
        runner: PlayerScriptRunner,
    ): WorldCycle {
        val movement = PlayerMovementProcess(OpenCollisionMap)
        val triggers = PlayerTriggerProcess(runner)
        return WorldCycle(
            world,
            WorldCommandQueue(capacity = 8),
            PlayerActionProcess(
                movement,
                PlayerChatActionProcess(
                    HuffmanCodec(ByteArray(256) { 8 }),
                    ChatAuditSink { true },
                ),
                scripts,
                runner,
                RouteSearchBudget(RouteSearchConfig()),
            ),
            PlayerScriptProcess(runner, triggers),
            TestPlayerContent.movementCycle(movement),
            PlayerLifecycleProcess(CharacterWriteQueue { DurableCharacterWrite }, triggers),
            PlayerOutputProcess(),
        )
    }
}
