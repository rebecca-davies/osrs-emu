package emu.server.game.runtime.lifecycle

import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.IncomingPlayerActionQueueConfig
import emu.game.content.ui.config.UiComponentMap
import emu.game.script.execution.PlayerScriptRequest
import emu.game.script.execution.PlayerScriptRunner
import emu.game.script.queue.PlayerQueueType
import emu.game.script.trigger.PlayerScriptRepository
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord
import emu.persistence.character.write.CharacterWriteQueue
import emu.persistence.character.write.DurableCharacterWrite
import emu.server.game.TestPlayerContent
import emu.server.game.network.output.GameOutputSink
import emu.server.game.runtime.command.WorldCommandQueue
import emu.server.game.world.World
import emu.server.game.world.activateTestPlayer
import emu.server.game.world.addTestPlayer
import emu.server.game.world.cycle.WorldCycle
import emu.server.game.world.cycle.PlayerPhase
import emu.server.game.world.player.PlayerLifecycle
import emu.server.game.world.testWorld
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

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
        val world = testWorld(maxPlayerIndex = 1)
        val action = PlayerQueueType.unit("never_ready")
        val scripts =
            PlayerScriptRepository.build(UiComponentMap.parse("[components]")) {
                onQueue(action) { }
            }
        val runner = PlayerScriptRunner(scripts)
        val player =
            world.addTestPlayer(
                CharacterRecord(1, "Player1", CharacterPosition(3200, 3200, 0), 0),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink { true },
            )
        world.activateTestPlayer(world.session(player).token)
        player.actionQueue.add(
            PlayerScriptRequest(scripts.require(action)),
            delayTicks = Int.MAX_VALUE,
        )
        val runtime = WorldRuntime(cycle(world, runner), tickInterval = 1.milliseconds)

        withTimeout(5.seconds) { runtime.run(maxTicks = 0) }

        assertFalse(world.contains(player.id))
    }

    private fun cycle(): WorldCycle {
        val world = testWorld()
        return TestPlayerContent.cycle(world, WorldCommandQueue(capacity = 8))
    }

    private fun cycle(world: World, runner: PlayerScriptRunner): WorldCycle =
        WorldCycle(
            world,
            WorldCommandQueue(capacity = 8),
            TestPlayerContent.actions(),
            PlayerPhase(runner),
            PlayerLifecycle(world, CharacterWriteQueue { DurableCharacterWrite }, runner),
            TestPlayerContent.output(world),
        )
}
