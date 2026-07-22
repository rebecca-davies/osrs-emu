package emu.server.game.network.output.playerinfo

import emu.crypto.NopStreamCipher
import emu.game.map.MapInstance
import emu.game.map.Tile
import emu.game.pathfinding.movement.MovementUpdate
import emu.protocol.osrs239.game.codec.playerinfo.PlayerInfoEncoder
import emu.protocol.osrs239.game.message.chat.PlayerPublicChat
import emu.protocol.osrs239.game.message.playerinfo.PlayerAppearance
import emu.protocol.osrs239.game.message.playerinfo.PlayerInfo
import emu.protocol.osrs239.game.message.playerinfo.PlayerSequence
import emu.server.game.network.wire.Rev239PlayerInfoOracle
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlayerInfoWireLifecycleTest {
    @Test
    fun `near teleport preserves signed deltas and plane in the client oracle`() {
        val observer = snapshot(index = 1, x = 3_200, name = "Observer")
        val state = PlayerInfoState(localIndex = observer.index)
        val oracle = Rev239PlayerInfoOracle(observer.index, observer.position)
        decode(oracle, state.next(PlayerInfoView(listOf(observer))))
        val destination = Tile(3_185, 3_215, 3)
        val teleported =
            observer.copy(
                position = destination,
                movement = MovementUpdate.Teleport(-15, 15, 3),
            )

        val update =
            assertNotNull(
                decode(oracle, state.next(PlayerInfoView(listOf(teleported))))
                    .updates[observer.index],
            )

        assertEquals(Rev239PlayerInfoOracle.UpdateType.TELEPORT, update.type)
        assertEquals(destination, update.position)
        assertEquals(127, update.temporaryMoveSpeed)
    }

    @Test
    fun `far teleport moves the local player and clears interpolation in the client oracle`() {
        val observer =
            snapshot(index = 1, x = 3_127, name = "Observer")
                .copy(position = Tile(3_127, 3_621))
        val state = PlayerInfoState(localIndex = observer.index)
        val oracle = Rev239PlayerInfoOracle(observer.index, observer.position)
        decode(oracle, state.next(PlayerInfoView(listOf(observer))))
        val destination = Tile(2_271, 5_332)
        val teleported =
            observer.copy(
                position = destination,
                movement = MovementUpdate.Teleport(-856, 1_711, 0),
                mapInstance = MapInstance.privateTo(1),
            )

        val update =
            assertNotNull(
                decode(oracle, state.next(PlayerInfoView(listOf(teleported))))
                    .updates[observer.index],
            )

        assertEquals(Rev239PlayerInfoOracle.UpdateType.TELEPORT, update.type)
        assertEquals(destination, update.position)
        assertEquals(127, update.temporaryMoveSpeed)
    }

    @Test
    fun `independent rev239 client oracle consumes add move chat remove and re-add lifecycle`() {
        val observer = snapshot(index = 1, x = 3_200, name = "Observer")
        val target = snapshot(index = 2, x = 3_210, name = "Target")
        val state = PlayerInfoState(localIndex = observer.index)
        val oracle = Rev239PlayerInfoOracle(observer.index, observer.position)

        val addition = decode(oracle, state.next(PlayerInfoView(listOf(observer, target))))
        val added = assertNotNull(addition.updates[target.index])
        assertEquals(Rev239PlayerInfoOracle.UpdateType.ADD, added.type)
        assertEquals(target.position, added.position)
        assertEquals("Target", added.appearanceName)
        assertEquals(-1, added.skullIcon)
        assertEquals(-1, added.prayerIcon)

        val ordinaryChat = PlayerPublicChat(0, 0, 255, byteArrayOf(104, 105))
        val movedTarget =
            target.copy(
                position = Tile(3_211, 3_200),
                movement = MovementUpdate.Walk(1, 0),
                publicChat = ordinaryChat,
            )
        val movement = decode(oracle, state.next(PlayerInfoView(listOf(observer, movedTarget))))
        val moved = assertNotNull(movement.updates[target.index])
        assertEquals(Rev239PlayerInfoOracle.UpdateType.WALK, moved.type)
        assertEquals(movedTarget.position, moved.position)
        assertContentEquals(ordinaryChat.encodedText, moved.chatText)
        assertNull(moved.pattern)

        val patternedChat =
            PlayerPublicChat(
                colour = 15,
                effect = 2,
                modIcon = 1,
                encodedText = byteArrayOf(1, 2, 3),
                pattern = byteArrayOf(4, 5, 6),
                autotyper = true,
            )
        val patterned =
            decode(
                oracle,
                state.next(
                    PlayerInfoView(
                        listOf(
                            observer,
                            movedTarget.copy(
                                movement = MovementUpdate.Idle,
                                publicChat = patternedChat,
                            ),
                        ),
                    ),
                ),
            ).updates.getValue(target.index)
        assertEquals(15, patterned.chatColour)
        assertEquals(2, patterned.chatEffect)
        assertEquals(1, patterned.modIcon)
        assertEquals(true, patterned.autotyper)
        assertContentEquals(patternedChat.encodedText, patterned.chatText)
        assertContentEquals(patternedChat.pattern, patterned.pattern)

        val animated =
            decode(
                oracle,
                state.next(
                    PlayerInfoView(
                        listOf(
                            observer,
                            movedTarget.copy(
                                movement = MovementUpdate.Idle,
                                publicChat = null,
                                sequence = PlayerSequence(1234, delay = 2),
                            ),
                        ),
                    ),
                ),
            ).updates.getValue(target.index)
        assertEquals(1234, animated.sequenceId)
        assertEquals(2, animated.sequenceDelay)

        val removal = decode(oracle, state.next(PlayerInfoView(listOf(observer))))
        assertEquals(Rev239PlayerInfoOracle.UpdateType.REMOVE, removal.updates[target.index]?.type)
        assertNull(oracle.player(target.index))

        val readdedTarget = target.copy(position = movedTarget.position)
        val readdition = decode(oracle, state.next(PlayerInfoView(listOf(observer, readdedTarget))))
        val readded = assertNotNull(readdition.updates[target.index])
        assertEquals(Rev239PlayerInfoOracle.UpdateType.ADD, readded.type)
        assertEquals(readdedTarget.position, oracle.player(target.index)?.position)
        assertEquals("Target", readded.appearanceName)
    }

    private fun decode(
        oracle: Rev239PlayerInfoOracle,
        info: PlayerInfo,
    ): Rev239PlayerInfoOracle.Cycle {
        val body = PlayerInfoEncoder.encode(NopStreamCipher, info)
        return oracle.decode(body).also { assertEquals(body.size, it.bytesConsumed) }
    }

    private fun snapshot(index: Int, x: Int, name: String) =
        PlayerInfoSnapshot(
            index = index,
            position = Tile(x, 3_200),
            movement = MovementUpdate.Idle,
            runEnabled = false,
            appearance = PlayerAppearance(name = name),
        )
}
