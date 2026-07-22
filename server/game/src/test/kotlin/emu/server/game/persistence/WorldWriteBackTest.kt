package emu.server.game.persistence

import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.IncomingPlayerActionQueueConfig
import emu.game.map.Tile
import emu.game.pathfinding.route.PathRoute
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord
import emu.persistence.character.model.CharacterSave
import emu.persistence.character.write.CharacterWriteCompletion
import emu.persistence.character.write.CharacterWriteQueue
import emu.persistence.character.write.CharacterWriteState
import emu.protocol.osrs239.game.message.player.Logout
import emu.server.game.TestPlayerContent
import emu.server.game.network.output.GameOutputBatch
import emu.server.game.network.output.GameOutputSegment
import emu.server.game.network.output.GameOutputSink
import emu.server.game.runtime.command.WorldCommandQueue
import emu.server.game.world.activateTestPlayer
import emu.server.game.world.addTestPlayer
import emu.server.game.world.cycle.WorldCycle
import emu.server.game.world.testWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorldWriteBackTest {
    @Test
    fun `logout retains identity and retries the same snapshot until write back accepts it`() {
        val attempts = mutableListOf<CharacterSave>()
        var durable = false
        val world =
            testWorld(
                maxPlayerIndex = 1,
                sessionStartedNanos = { 1_000_000_000L },
            )
        val cycle =
            WorldCycle(
                world = world,
                commands = WorldCommandQueue(capacity = 8),
                actions = TestPlayerContent.actions(),
                playerPhase = TestPlayerContent.playerPhase(),
                lifecycle =
                    TestPlayerContent.lifecycle(
                        world,
                        CharacterWriteQueue { save ->
                            attempts += save
                            if (attempts.size == 1) {
                                null
                            } else {
                                CharacterWriteCompletion {
                                    if (durable) CharacterWriteState.DURABLE else CharacterWriteState.PENDING
                                }
                            }
                        },
                        nanoTime = { 2_000_000_000L },
                    ),
                output = TestPlayerContent.output(world),
            )
        val output = mutableListOf<GameOutputBatch>()
        val player =
            world.addTestPlayer(
                CharacterRecord(
                    id = 1,
                    displayName = "Player1",
                    position = CharacterPosition(3200, 3200, 0),
                    playTimeSeconds = 36,
                ),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink { batch -> output += batch; true },
            )
        world.activateTestPlayer(world.session(player).token)
        player.movement.queueRoute(
            PathRoute(listOf(Tile(3_203, 3_200)), alternative = false, success = true),
        )
        world.requestLogout(world.session(player).token)

        cycle.tick(worldTick = 0)

        assertTrue(world.contains(player.id), "a rejected save must retain the identity and index")
        assertTrue(output.none(::containsLogout))

        cycle.tick(worldTick = 1)

        assertTrue(world.contains(player.id), "queued write-back must retain the online identity")
        assertTrue(output.none(::containsLogout))
        assertEquals(
            attempts.first().position.x,
            player.movement.position.x,
            "movement must stop after the immutable logout snapshot is taken",
        )

        cycle.tick(worldTick = 2)

        assertTrue(world.contains(player.id))
        assertEquals(2, attempts.size, "one queued snapshot must be polled instead of resubmitted")

        durable = true
        cycle.tick(worldTick = 3)

        assertFalse(world.contains(player.id))
        assertEquals(2, attempts.size)
        assertEquals(attempts.first(), attempts.last(), "write-back retries must use one immutable snapshot")
        assertEquals(CharacterPosition(3201, 3200, 0), attempts.first().position)
        assertEquals(37, attempts.first().playTimeSeconds)
        val logout = output.single(::containsLogout)
        val packets = (logout.segments.single() as GameOutputSegment.Packets).messages
        assertEquals(listOf(Logout), packets)
    }

    private fun containsLogout(batch: GameOutputBatch): Boolean =
        batch.segments
            .filterIsInstance<GameOutputSegment.Packets>()
            .flatMap(GameOutputSegment.Packets::messages)
            .contains(Logout)
}
