package emu.server.world.runtime

import emu.server.world.cycle.WorldCycle
import emu.compression.HuffmanCodec
import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PlayerMovementProcess
import emu.game.action.GameInputQueue
import emu.game.action.GameInputQueueConfig
import emu.game.content.ui.UiComponentMap
import emu.game.script.PlayerQueueType
import emu.game.script.PlayerScriptRepository
import emu.game.script.PlayerScriptRequest
import emu.game.script.PlayerScriptRunner
import emu.persistence.character.PlayerPosition
import emu.persistence.character.PlayerRecord
import emu.persistence.character.CharacterWriteQueue
import emu.persistence.character.DurableCharacterWrite
import emu.persistence.chat.ChatAuditSink
import emu.server.world.player.PlayerActionProcess
import emu.server.world.TestPlayerContent
import emu.server.world.player.PlayerChatActionProcess
import emu.server.world.player.PlayerLifecycleProcess
import emu.server.world.player.PlayerOutputProcess
import emu.server.world.player.PlayerScriptProcess
import emu.server.world.player.PlayerTriggerProcess
import emu.server.world.network.GameOutputSink
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WorldRuntimeTest {
    @Test
    fun `runtime owns one finite authoritative clock`() = runBlocking {
        val runtime = WorldRuntime(cycle(), tickInterval = 1.milliseconds)

        runtime.run(maxTicks = 3)

        assertFailsWith<IllegalStateException> { runtime.run(maxTicks = 1) }
    }

    @Test
    fun `runtime rejects a sub-millisecond clock`() {
        assertFailsWith<IllegalArgumentException> {
            WorldRuntime(cycle(), tickInterval = 0.milliseconds)
        }
    }

    @Test
    fun `shutdown force-removes non-discardable work after the bounded terminal cycle`() = runBlocking {
        val world = testGameWorld(maxPlayerIndex = 1)
        val action = PlayerQueueType.unit("never_ready")
        val scripts =
            PlayerScriptRepository.build(UiComponentMap.parse("[components]")) {
                onQueue(action) { }
            }
        val runner = PlayerScriptRunner(scripts)
        val connected =
            world.addTestPlayer(
                PlayerRecord(1, "Player1", PlayerPosition(3200, 3200, 0), 0),
                GameInputQueue(GameInputQueueConfig()),
                GameOutputSink { true },
            )
        world.activateTestPlayer(connected.connection.token)
        connected.player.actionQueue.add(
            PlayerScriptRequest(scripts.require(action)),
            delayTicks = Int.MAX_VALUE,
        )
        val runtime = WorldRuntime(cycle(world, runner), tickInterval = 1.milliseconds)

        withTimeout(5.seconds) { runtime.run(maxTicks = 0) }

        assertFalse(world.contains(connected.player.id))
    }

    private fun cycle(): WorldCycle {
        val movement = PlayerMovementProcess(OpenCollisionMap)
        return WorldCycle(
            testGameWorld(),
            WorldCommandQueue(capacity = 8),
            TestPlayerContent.actions(
                movement,
                PlayerChatActionProcess(
                    HuffmanCodec(ByteArray(256) { 8 }),
                    ChatAuditSink { true },
                ),
            ),
            TestPlayerContent.scripts(),
            TestPlayerContent.movementCycle(movement),
            TestPlayerContent.lifecycle(CharacterWriteQueue { DurableCharacterWrite }),
            PlayerOutputProcess(),
        )
    }

    private fun cycle(world: GameWorld, runner: PlayerScriptRunner): WorldCycle {
        val movement = PlayerMovementProcess(OpenCollisionMap)
        val triggers = PlayerTriggerProcess(runner)
        return WorldCycle(
            world,
            WorldCommandQueue(capacity = 8),
            TestPlayerContent.actions(
                movement,
                PlayerChatActionProcess(
                    HuffmanCodec(ByteArray(256) { 8 }),
                    ChatAuditSink { true },
                ),
            ),
            PlayerScriptProcess(runner, triggers),
            TestPlayerContent.movementCycle(movement),
            PlayerLifecycleProcess(CharacterWriteQueue { DurableCharacterWrite }, triggers),
            PlayerOutputProcess(),
        )
    }
}
