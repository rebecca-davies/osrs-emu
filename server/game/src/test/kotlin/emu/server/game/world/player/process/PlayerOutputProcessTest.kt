package emu.server.game.world.player.process

import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.IncomingPlayerActionQueueConfig
import emu.game.content.player.PlayerVarpCatalog
import emu.game.cycle.CycleProfileSnapshot
import emu.game.cycle.CyclePhase
import emu.game.cycle.CyclePhaseProfileSnapshot
import emu.game.map.Tile
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.pathfinding.movement.MovementUpdate
import emu.game.pathfinding.route.PathRoute
import emu.game.ui.Component
import emu.persistence.character.model.CharacterPosition
import emu.persistence.character.model.CharacterRecord
import emu.persistence.character.write.DurableCharacterWrite
import emu.protocol.osrs239.game.message.chat.MessageGame
import emu.protocol.osrs239.game.message.component.IfCloseSub
import emu.protocol.osrs239.game.message.component.IfOpenSub
import emu.protocol.osrs239.game.message.component.IfOpenSub.Companion.MODAL
import emu.protocol.osrs239.game.message.cycle.ServerTickEnd
import emu.protocol.osrs239.game.message.scene.RebuildNormal
import emu.protocol.osrs239.game.message.varp.VarpSmall
import emu.server.game.network.output.GameOutputBatch
import emu.server.game.network.output.GameOutputSegment
import emu.server.game.network.output.GameOutputSink
import emu.server.game.world.World
import emu.server.game.world.activateTestPlayer
import emu.server.game.world.addTestPlayer
import emu.server.game.world.testWorld
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
        val world = testWorld(maxPlayerIndex = 1)
        val connected =
            world.addTestPlayer(
                CharacterRecord(1, "Player1", CharacterPosition(3_200, 3_200, 0), 0),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink { batches += it; true },
            )
        world.activateTestPlayer(connected.connection.token)
        connected.player.interfaces.openModal(Component.of(161, 1), 200)
        connected.player.closeModal()

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
        val world = testWorld(maxPlayerIndex = 2)
        val observer =
            world.addTestPlayer(
                CharacterRecord(1, "Observer", CharacterPosition(3_200, 3_200, 0), 0),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink { true },
            )
        val target =
            world.addTestPlayer(
                CharacterRecord(2, "Target", CharacterPosition(3_201, 3_200, 0), 0),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
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
        val world = testWorld(maxPlayerIndex = 1)
        val connected =
            world.addTestPlayer(
                CharacterRecord(1, "Player1", CharacterPosition(3_200, 3_200, 0), 0),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
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
        val world = testWorld(maxPlayerIndex = 1)
        val connected =
            world.addTestPlayer(
                CharacterRecord(1, "Player1", CharacterPosition(3_200, 3_200, 0), 0),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
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
        val world = testWorld(maxPlayerIndex = 1)
        val connected =
            world.addTestPlayer(
                CharacterRecord(1, "Player1", CharacterPosition(3_200, 3_200, 0), 0),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
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
        val world = testWorld(maxPlayerIndex = 1)
        val connected =
            world.addTestPlayer(
                CharacterRecord(
                    1,
                    "Player1",
                    CharacterPosition(3222, 3218, 0),
                    0,
                ),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
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
        output.prepare(connected, output.snapshot(world.allPlayers()), null)
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
    fun `cycle profile is included in ordinary player output`() {
        val batches = mutableListOf<GameOutputBatch>()
        val world = testWorld(maxPlayerIndex = 1)
        val connected =
            world.addTestPlayer(
                CharacterRecord(
                    1,
                    "Player",
                    CharacterPosition(3222, 3218, 0),
                    0,
                ),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink { batch -> batches += batch; true },
            )
        world.activateTestPlayer(connected.connection.token)
        val snapshot =
            CycleProfileSnapshot(
                50,
                2_000_000,
                8_000_000,
                1,
                30_000_000_000,
                phases =
                    listOf(
                        CyclePhaseProfileSnapshot(CyclePhase.INFO, 1_500_000, 4_000_000),
                        CyclePhaseProfileSnapshot(CyclePhase.PLAYER, 500_000, 2_000_000),
                    ),
            )
        world.recordCycleProfile(snapshot)

        val output = PlayerOutputProcess()
        val view = output.snapshot(world.allPlayers())
        output.prepare(connected, view, output.profileMessage(snapshot, view.playerCount))
        output.publish(connected)

        val messages =
            batches.single().segments
                .filterIsInstance<GameOutputSegment.Packets>()
                .flatMap(GameOutputSegment.Packets::messages)
        val report = messages.filterIsInstance<MessageGame>().single()
        assertTrue("players=1" in report.text)
        assertTrue("avg=2.0ms" in report.text)
        assertTrue("max=8.0ms" in report.text)
        assertTrue("hot=info:1.5/player:0.5 ms" in report.text)
    }
}
