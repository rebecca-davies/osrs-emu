package emu.server.game.network.output

import emu.compression.HuffmanCodec
import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.IncomingPlayerActionQueueConfig
import emu.game.content.player.PlayerVarpCatalog
import emu.game.cycle.CycleProfileSnapshot
import emu.game.cycle.CyclePhase
import emu.game.cycle.CyclePhaseProfileSnapshot
import emu.game.map.Tile
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
import emu.server.game.world.activateTestPlayer
import emu.server.game.world.addTestPlayer
import emu.server.game.world.testWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerOutputTest {
    @Test
    fun `modal closure publishes an exact close-sub destination before player information`() {
        val batches = mutableListOf<GameOutputBatch>()
        val world = testWorld(maxPlayerIndex = 1)
        val player =
            world.addTestPlayer(
                CharacterRecord(1, "Player1", CharacterPosition(3_200, 3_200, 0), 0),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink { batches += it; true },
            )
        world.activateTestPlayer(world.session(player).token)
        player.interfaces.openModal(Component.of(161, 1), 200)
        player.closeModal()

        val output = PlayerOutput(world, HuffmanCodec(ByteArray(256) { 8 }))
        output.prepare(player, output.snapshot(world.allPlayers()), null)
        output.publish(player)

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
        world.activateTestPlayer(world.session(observer).token)
        world.activateTestPlayer(world.session(target).token)
        target.requestLogout()
        assertTrue(target.beginLogout())

        val output = PlayerOutput(world, HuffmanCodec(ByteArray(256) { 8 }))
        val view = output.snapshot(world.allPlayers())

        assertNull(view[target.index])
        output.prepare(target, view, null)
        assertNull(world.session(target).pendingOutput)
    }

    @Test
    fun `rejected cycle output disconnects the desynchronized client and removes it after write back`() {
        val world = testWorld(maxPlayerIndex = 1)
        val player =
            world.addTestPlayer(
                CharacterRecord(1, "Player1", CharacterPosition(3_200, 3_200, 0), 0),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink { false },
            )
        world.activateTestPlayer(world.session(player).token)
        val output = PlayerOutput(world, HuffmanCodec(ByteArray(256) { 8 }))

        output.prepare(player, output.snapshot(world.allPlayers()), null)
        output.publish(player)

        assertFalse(world.session(player).isConnected)
        assertTrue(player.logoutRequested)
        world.writeBack(player).completion = DurableCharacterWrite
        output.prepare(player, output.snapshot(world.allPlayers()), null)
        output.cleanup(player)

        assertFalse(world.contains(player.id))
    }

    @Test
    fun `durable logout is retried until the connection accepts it`() {
        var acceptsOutput = false
        var attempts = 0
        val world = testWorld(maxPlayerIndex = 1)
        val player =
            world.addTestPlayer(
                CharacterRecord(1, "Player1", CharacterPosition(3_200, 3_200, 0), 0),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink {
                    attempts++
                    acceptsOutput
                },
            )
        world.writeBack(player).completion = DurableCharacterWrite
        val output = PlayerOutput(world, HuffmanCodec(ByteArray(256) { 8 }))
        val view = output.snapshot(world.allPlayers())

        output.prepare(player, view, null)
        output.publish(player)
        output.cleanup(player)

        assertTrue(world.contains(player.id))
        acceptsOutput = true
        output.prepare(player, view, null)
        output.publish(player)
        output.cleanup(player)

        assertEquals(2, attempts)
        assertFalse(world.contains(player.id))
    }

    @Test
    fun `durable logout stops retrying a permanently saturated connection`() {
        var attempts = 0
        val world = testWorld(maxPlayerIndex = 1)
        val player =
            world.addTestPlayer(
                CharacterRecord(1, "Player1", CharacterPosition(3_200, 3_200, 0), 0),
                IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig()),
                GameOutputSink {
                    attempts++
                    false
                },
            )
        val session = world.session(player)
        world.writeBack(player).completion = DurableCharacterWrite
        val output = PlayerOutput(world, HuffmanCodec(ByteArray(256) { 8 }))
        val view = output.snapshot(world.allPlayers())

        repeat(3) {
            output.prepare(player, view, null)
            output.publish(player)
            output.cleanup(player)
        }

        assertEquals(3, attempts)
        assertFalse(session.isConnected)
        assertFalse(world.contains(player.id))
    }

    @Test
    fun `scene rebuild and varp precede the globally prepared player-info group`() {
        val batches = mutableListOf<GameOutputBatch>()
        val world = testWorld(maxPlayerIndex = 1)
        val player =
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
        world.activateTestPlayer(world.session(player).token)
        player.movement.queueRoute(
            PathRoute(listOf(Tile(3256, 3218)), alternative = false, success = true),
        )
        repeat(33) {
            world.advanceMovement(player)
            player.movement.finishCycle()
        }
        player.varps[PlayerVarpCatalog.RUN_MODE] = 1
        world.advanceMovement(player)
        assertEquals(MovementUpdate.Walk(1, 0), player.movement.update)

        val output = PlayerOutput(world, HuffmanCodec(ByteArray(256) { 8 }))
        output.prepare(player, output.snapshot(world.allPlayers()), null)
        output.publish(player)
        output.cleanup(player)

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
        val player =
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
        world.activateTestPlayer(world.session(player).token)
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

        val output = PlayerOutput(world, HuffmanCodec(ByteArray(256) { 8 }))
        val view = output.snapshot(world.allPlayers())
        output.prepare(player, view, output.profileMessage(snapshot, view.playerCount))
        output.publish(player)

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
