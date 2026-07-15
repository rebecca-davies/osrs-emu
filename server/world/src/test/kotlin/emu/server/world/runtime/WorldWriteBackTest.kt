package emu.server.world.runtime

import emu.server.world.cycle.WorldCycle
import emu.compression.HuffmanCodec
import emu.game.action.GameInputQueue
import emu.game.action.GameInputQueueConfig
import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PathRoute
import emu.game.pathfinding.PlayerMovementProcess
import emu.game.pathfinding.Tile
import emu.persistence.character.CharacterWriteQueue
import emu.persistence.character.CharacterWriteCompletion
import emu.persistence.character.CharacterWriteState
import emu.persistence.character.PlayerPosition
import emu.persistence.character.PlayerRecord
import emu.persistence.character.PlayerSessionSave
import emu.persistence.chat.ChatAuditSink
import emu.protocol.osrs239.game.message.Logout
import emu.server.world.network.GameOutputBatch
import emu.server.world.network.GameOutputSegment
import emu.server.world.network.GameOutputSink
import emu.server.world.player.PlayerActionProcess
import emu.server.world.TestPlayerContent
import emu.server.world.player.PlayerChatActionProcess
import emu.server.world.player.PlayerLifecycleProcess
import emu.server.world.player.PlayerOutputProcess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorldWriteBackTest {
    @Test
    fun `logout retains identity and retries the same snapshot until write back accepts it`() {
        val attempts = mutableListOf<PlayerSessionSave>()
        var durable = false
        val world =
            testGameWorld(
                maxPlayerIndex = 1,
                sessionStartedNanos = { 1_000_000_000L },
            )
        val movement = PlayerMovementProcess(OpenCollisionMap)
        val cycle =
            WorldCycle(
                world = world,
                commands = WorldCommandQueue(capacity = 8),
                actions =
                    TestPlayerContent.actions(
                        movement,
                        PlayerChatActionProcess(
                            HuffmanCodec(ByteArray(256) { 8 }),
                            ChatAuditSink { true },
                        ),
                    ),
                scripts = TestPlayerContent.scripts(),
                movement = TestPlayerContent.movementCycle(movement),
                lifecycle =
                    PlayerLifecycleProcess(
                        writes =
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
                        triggers = TestPlayerContent.triggers(),
                        nanoTime = { 2_000_000_000L },
                    ),
                output = PlayerOutputProcess(),
            )
        val output = mutableListOf<GameOutputBatch>()
        val connected =
            world.addTestPlayer(
                PlayerRecord(
                    id = 1,
                    displayName = "Player1",
                    position = PlayerPosition(3200, 3200, 0),
                    playTimeSeconds = 36,
                ),
                GameInputQueue(GameInputQueueConfig()),
                GameOutputSink { batch -> output += batch; true },
            )
        world.activateTestPlayer(connected.connection.token)
        connected.player.movement.queueRoute(
            PathRoute(listOf(Tile(3_203, 3_200)), alternative = false, success = true),
        )
        world.requestLogout(connected.connection.token)

        cycle.tick(worldTick = 0)

        assertTrue(world.contains(connected.player.id), "a rejected save must retain the identity and index")
        assertTrue(output.none(::containsLogout))

        cycle.tick(worldTick = 1)

        assertTrue(world.contains(connected.player.id), "queued write-back must retain the online identity")
        assertTrue(output.none(::containsLogout))
        assertEquals(
            attempts.first().position.x,
            connected.player.movement.position.x,
            "movement must stop after the immutable logout snapshot is taken",
        )

        cycle.tick(worldTick = 2)

        assertTrue(world.contains(connected.player.id))
        assertEquals(2, attempts.size, "one queued snapshot must be polled instead of resubmitted")

        durable = true
        cycle.tick(worldTick = 3)

        assertFalse(world.contains(connected.player.id))
        assertEquals(2, attempts.size)
        assertEquals(attempts.first(), attempts.last(), "write-back retries must use one immutable snapshot")
        assertEquals(PlayerPosition(3201, 3200, 0), attempts.first().position)
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
