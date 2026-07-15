package emu.server.game.player

import emu.compression.HuffmanCodec
import emu.game.chat.ChatFilterInput
import emu.game.chat.PublicChatInput
import emu.game.varp.PlayerVarps
import kotlinx.coroutines.runBlocking
import emu.persistence.ChatAuditMessage
import emu.persistence.ChatAuditSink
import emu.persistence.PlayerRank
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlayerChatStateTest {
    private val huffman = HuffmanCodec(ByteArray(256) { 8 })

    @Test fun `filter actions update sparse permanent settings only on the cycle`() = runBlocking {
        val varps = PlayerVarps(PlayerVarpTypes.CATALOG).apply { markClientSynchronized() }
        val actions = playerChatActions(1, PlayerRank.PLAYER, varps, PlayerChatState(), ChatAuditSink { true }, huffman)

        actions.dispatch(ChatFilterInput(3, 1, 2))

        assertEquals(3, varps[PlayerVarpTypes.PUBLIC_CHAT_FILTER])
        assertEquals(1, varps[PlayerVarpTypes.PRIVATE_CHAT_FILTER])
        assertEquals(2, varps[PlayerVarpTypes.TRADE_CHAT_FILTER])
        assertEquals(
            mapOf(65533 to 3, 65534 to 1, 65535 to 2),
            varps.dirtyPersistentValues(),
        )
        assertEquals(emptyList(), varps.drainClientUpdates(), "chat settings use dedicated packets, not varp updates")
    }

    @Test fun `public chat is published only after content-free audit admission`() = runBlocking {
        val audits = mutableListOf<ChatAuditMessage>()
        val state = PlayerChatState()
        val actions =
            playerChatActions(
                42,
                PlayerRank.MODERATOR,
                PlayerVarps(PlayerVarpTypes.CATALOG),
                state,
                ChatAuditSink { audits += it; true },
                huffman,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            )

        actions.dispatch(PublicChatInput(1, 2, "hello", null))

        assertEquals(listOf(ChatAuditMessage(42, emu.persistence.ChatChannel.PUBLIC, "hello", Instant.EPOCH)), audits)
        val published = assertNotNull(state.takePublicChat())
        assertEquals(1, published.modIcon)
        assertEquals("hello", huffman.decode(published.encodedText))

        val rejectedState = PlayerChatState()
        val rejected = playerChatActions(
            42,
            PlayerRank.PLAYER,
            PlayerVarps(PlayerVarpTypes.CATALOG),
            rejectedState,
            ChatAuditSink { false },
            huffman,
        )
        rejected.dispatch(PublicChatInput(0, 0, "not audited", null))
        assertNull(rejectedState.takePublicChat())
    }
}
