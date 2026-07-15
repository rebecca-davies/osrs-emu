package emu.server.world.player

import emu.compression.HuffmanCodec
import emu.game.chat.ChatFilterInput
import emu.game.chat.PublicChatInput
import emu.game.varp.PlayerVarps
import emu.persistence.account.PlayerRank
import emu.persistence.chat.ChatAuditMessage
import emu.persistence.chat.ChatAuditSink
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlayerChatActionsTest {
    private val huffman = HuffmanCodec(ByteArray(256) { 8 })

    @Test fun `filter actions update sparse permanent settings only on the cycle`() = runBlocking {
        val varps = PlayerVarps(PlayerVarpCatalog.ALL).apply { markClientSynchronized() }
        val actions =
            playerChatActions(
                1,
                PlayerRank.PLAYER,
                varps,
                PlayerPublicChatState(),
                ChatAuditSink { true },
                huffman,
            )

        actions.dispatch(ChatFilterInput(3, 1, 2))

        assertEquals(3, varps[PlayerVarpCatalog.PUBLIC_CHAT_FILTER])
        assertEquals(1, varps[PlayerVarpCatalog.PRIVATE_CHAT_FILTER])
        assertEquals(2, varps[PlayerVarpCatalog.TRADE_CHAT_FILTER])
        assertEquals(
            mapOf(65533 to 3, 65534 to 1, 65535 to 2),
            varps.dirtyPersistentValues(),
        )
        assertEquals(emptyList(), varps.drainClientUpdates(), "chat settings use dedicated packets, not varp updates")
    }

    @Test fun `public chat is published only after content-free audit admission`() = runBlocking {
        val audits = mutableListOf<ChatAuditMessage>()
        val state = PlayerPublicChatState()
        val actions =
            playerChatActions(
                42,
                PlayerRank.MODERATOR,
                PlayerVarps(PlayerVarpCatalog.ALL),
                state,
                ChatAuditSink { audits += it; true },
                huffman,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            )

        actions.dispatch(PublicChatInput(1, 2, "hello", null))

        assertEquals(listOf(ChatAuditMessage(42, emu.persistence.chat.ChatChannel.PUBLIC, "hello", Instant.EPOCH)), audits)
        val published = assertNotNull(state.take())
        assertEquals(1, published.modIcon)
        assertEquals("hello", huffman.decode(published.encodedText))

        val rejectedState = PlayerPublicChatState()
        val rejected =
            playerChatActions(
                42,
                PlayerRank.PLAYER,
                PlayerVarps(PlayerVarpCatalog.ALL),
                rejectedState,
                ChatAuditSink { false },
                huffman,
            )
        rejected.dispatch(PublicChatInput(0, 0, "not audited", null))
        assertNull(rejectedState.take())
    }
}
