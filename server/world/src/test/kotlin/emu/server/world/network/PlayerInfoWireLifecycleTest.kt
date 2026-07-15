package emu.server.world.network

import emu.crypto.NopStreamCipher
import emu.game.pathfinding.MovementUpdate
import emu.game.pathfinding.Tile
import emu.protocol.osrs239.game.codec.PlayerInfoEncoder
import emu.protocol.osrs239.game.message.PlayerAppearance
import emu.protocol.osrs239.game.message.PlayerInfo
import emu.protocol.osrs239.game.message.PlayerPublicChat
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlayerInfoWireLifecycleTest {
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
