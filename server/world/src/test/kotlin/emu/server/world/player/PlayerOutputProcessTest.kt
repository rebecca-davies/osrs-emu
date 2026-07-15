package emu.server.world.player

import emu.game.action.GameInputQueue
import emu.game.action.GameInputQueueConfig
import emu.game.cycle.CycleProfileSnapshot
import emu.game.content.player.PlayerVarpCatalog
import emu.game.pathfinding.MovementUpdate
import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PathRoute
import emu.game.pathfinding.Tile
import emu.persistence.character.PlayerPosition
import emu.persistence.character.PlayerRecord
import emu.persistence.character.DurableCharacterWrite
import emu.persistence.account.PlayerRank
import emu.protocol.osrs239.game.message.MessageGame
import emu.protocol.osrs239.game.message.IfCloseSub
import emu.protocol.osrs239.game.message.IfOpenSub
import emu.protocol.osrs239.game.message.IfOpenSub.Companion.MODAL
import emu.protocol.osrs239.game.message.RebuildNormal
import emu.protocol.osrs239.game.message.ServerTickEnd
import emu.protocol.osrs239.game.message.VarpSmall
import emu.server.world.network.GameOutputBatch
import emu.server.world.network.GameOutputSegment
import emu.server.world.network.GameOutputSink
import emu.server.world.runtime.GameWorld
import emu.server.world.runtime.addTestPlayer
import emu.server.world.runtime.activateTestPlayer
import emu.server.world.runtime.testGameWorld
import emu.game.ui.Component
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerOutputProcessTest {
    @Test
    fun `modal closure publishes an exact close-sub destination before player information`() {
        val batches = mutableListOf<GameOutputBatch>()
        val world = testGameWorld(maxPlayerIndex = 1)
        val connected =
            world.addTestPlayer(
                PlayerRecord(1, "Player1", PlayerPosition(3_200, 3_200, 0), 0),
                GameInputQueue(GameInputQueueConfig()),
                GameOutputSink { batches += it; true },
            )
        world.activateTestPlayer(connected.connection.token)
        connected.player.interfaces.openModal(Component.of(161, 1), 200)
        connected.player.interfaces.closeModal()

        val output = PlayerOutputProcess()
        output.prepare(connected, output.snapshot(world.allPlayers()), null)
        output.publish(connected)

        val messages =
            batches.single().segments
                .filterIsInstance<GameOutputSegment.Packets>()
                .flatMap(GameOutputSegment.Packets::messages)
        assertEquals(
            listOf(IfOpenSub(161, 1, 200, MODAL), IfCloseSub(161, 1)),
            messages.take(2),
        )
    }

    @Test
    fun `logging out players leave the shared information view before persistence completes`() {
        val world = testGameWorld(maxPlayerIndex = 2)
        val observer =
            world.addTestPlayer(
                PlayerRecord(1, "Observer", PlayerPosition(3_200, 3_200, 0), 0),
                GameInputQueue(GameInputQueueConfig()),
                GameOutputSink { true },
            )
        val target =
            world.addTestPlayer(
                PlayerRecord(2, "Target", PlayerPosition(3_201, 3_200, 0), 0),
                GameInputQueue(GameInputQueueConfig()),
                GameOutputSink { true },
            )
        world.activateTestPlayer(observer.connection.token)
        world.activateTestPlayer(target.connection.token)
        target.player.requestLogout()
        assertTrue(target.player.beginLogout())

        val output = PlayerOutputProcess()
        val view = output.snapshot(world.allPlayers())

        assertNull(view[target.connection.playerIndex])
        output.prepare(target, view, null)
        assertNull(target.connection.pendingOutput)
    }

    @Test
    fun `rejected cycle output disconnects the desynchronized client and removes it after write back`() {
        val world = testGameWorld(maxPlayerIndex = 1)
        val connected =
            world.addTestPlayer(
                PlayerRecord(1, "Player1", PlayerPosition(3_200, 3_200, 0), 0),
                GameInputQueue(GameInputQueueConfig()),
                GameOutputSink { false },
            )
        world.activateTestPlayer(connected.connection.token)
        val output = PlayerOutputProcess()

        output.prepare(connected, output.snapshot(world.allPlayers()), null)
        output.publish(connected)

        assertFalse(connected.connection.isConnected)
        assertTrue(connected.player.logoutRequested)
        connected.writeBack.completion = DurableCharacterWrite
        output.prepare(connected, output.snapshot(world.allPlayers()), null)
        output.cleanup(world, connected)

        assertFalse(world.contains(connected.player.id))
    }

    @Test
    fun `durable logout is retried until the connection accepts it`() {
        var acceptsOutput = false
        var attempts = 0
        val world = testGameWorld(maxPlayerIndex = 1)
        val connected =
            world.addTestPlayer(
                PlayerRecord(1, "Player1", PlayerPosition(3_200, 3_200, 0), 0),
                GameInputQueue(GameInputQueueConfig()),
                GameOutputSink {
                    attempts++
                    acceptsOutput
                },
            )
        connected.writeBack.completion = DurableCharacterWrite
        val output = PlayerOutputProcess()
        val view = output.snapshot(world.allPlayers())

        output.prepare(connected, view, null)
        output.publish(connected)
        output.cleanup(world, connected)

        assertTrue(world.contains(connected.player.id))
        acceptsOutput = true
        output.prepare(connected, view, null)
        output.publish(connected)
        output.cleanup(world, connected)

        assertEquals(2, attempts)
        assertFalse(world.contains(connected.player.id))
    }

    @Test
    fun `durable logout stops retrying a permanently saturated connection`() {
        var attempts = 0
        val world = testGameWorld(maxPlayerIndex = 1)
        val connected =
            world.addTestPlayer(
                PlayerRecord(1, "Player1", PlayerPosition(3_200, 3_200, 0), 0),
                GameInputQueue(GameInputQueueConfig()),
                GameOutputSink {
                    attempts++
                    false
                },
            )
        connected.writeBack.completion = DurableCharacterWrite
        val output = PlayerOutputProcess()
        val view = output.snapshot(world.allPlayers())

        repeat(3) {
            output.prepare(connected, view, null)
            output.publish(connected)
            output.cleanup(world, connected)
        }

        assertEquals(3, attempts)
        assertFalse(connected.connection.isConnected)
        assertFalse(world.contains(connected.player.id))
    }

    @Test
    fun `scene rebuild and varp precede the globally prepared player-info group`() {
        val batches = mutableListOf<GameOutputBatch>()
        val world = testGameWorld(maxPlayerIndex = 1)
        val connected =
            world.addTestPlayer(
                PlayerRecord(
                    1,
                    "Player1",
                    PlayerPosition(3222, 3218, 0),
                    0,
                ),
                GameInputQueue(GameInputQueueConfig()),
                GameOutputSink { batch -> batches += batch; true },
            )
        world.activateTestPlayer(connected.connection.token)
        val player = connected.player
        player.movement.queueRoute(
            PathRoute(listOf(Tile(3256, 3218)), alternative = false, success = true),
        )
        repeat(33) {
            player.movement.process(OpenCollisionMap)
            player.movement.finishCycle()
        }
        player.varps[PlayerVarpCatalog.RUN_MODE] = 1
        player.movement.process(OpenCollisionMap)
        assertEquals(MovementUpdate.Walk(1, 0), player.movement.update)

        val output = PlayerOutputProcess()
        output.prepare(connected, output.snapshot(world.allPlayers()), world.cycleProfile)
        output.publish(connected)
        output.cleanup(world, connected)

        val segments = batches.single().segments
        val leading = assertIs<GameOutputSegment.Packets>(segments[0]).messages
        assertEquals(VarpSmall(PlayerVarpCatalog.RUN_MODE.id, 1), leading[0])
        assertIs<RebuildNormal>(assertIs<GameOutputSegment.Packets>(segments[1]).messages.single())
        assertIs<GameOutputSegment.PacketGroup>(segments[2])
        assertEquals(listOf(ServerTickEnd), assertIs<GameOutputSegment.Packets>(segments[3]).messages)
        assertEquals(MovementUpdate.Idle, player.movement.update)
    }

    @Test
    fun `cycle profile is included only in administrator output`() {
        val batches = mutableListOf<GameOutputBatch>()
        val world = testGameWorld(maxPlayerIndex = 1)
        val connected =
            world.addTestPlayer(
                PlayerRecord(
                    1,
                    "Admin",
                    PlayerPosition(3222, 3218, 0),
                    0,
                    rank = PlayerRank.ADMINISTRATOR,
                ),
                GameInputQueue(GameInputQueueConfig()),
                GameOutputSink { batch -> batches += batch; true },
            )
        world.activateTestPlayer(connected.connection.token)
        world.recordCycleProfile(CycleProfileSnapshot(50, 2_000_000, 8_000_000, 1, 30_000_000_000))

        val output = PlayerOutputProcess()
        output.prepare(connected, output.snapshot(world.allPlayers()), world.cycleProfile)
        output.publish(connected)

        val messages =
            batches.single().segments
                .filterIsInstance<GameOutputSegment.Packets>()
                .flatMap(GameOutputSegment.Packets::messages)
        assertEquals(1, messages.filterIsInstance<MessageGame>().size)
    }
}
