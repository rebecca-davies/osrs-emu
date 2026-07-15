package emu.protocol.osrs239

import emu.protocol.osrs239.game.gameModule
import emu.protocol.osrs239.game.message.PlayerInfo
import emu.protocol.osrs239.game.message.RebuildLogin
import emu.protocol.osrs239.game.message.RebuildNormal
import emu.protocol.osrs239.game.message.IfButtonX
import emu.protocol.osrs239.game.message.Logout
import emu.protocol.osrs239.game.message.MessagePublic
import emu.protocol.osrs239.game.message.SetChatFilterSettings
import emu.protocol.osrs239.game.prot.GameClientProt
import emu.protocol.osrs239.js5.js5Module
import emu.protocol.osrs239.js5.message.Js5GroupResponse
import emu.protocol.osrs239.js5.prot.Js5Prot
import emu.protocol.osrs239.login.loginModule
import emu.protocol.osrs239.login.message.LoginResponse
import emu.protocol.osrs239.login.message.ServerSessionKey
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Verifies that every domain codec is collected into the assembled registry. */
class CodecRepositoriesTest {
    private val repository = koinApplication { modules(js5Module, loginModule, gameModule) }.koin.buildCodecRepository()

    @Test fun `every js5 decoder opcode is present`() {
        assertNotNull(repository.decoder(Js5Prot.GROUP_REQUEST.opcode), "urgent group-request decoder")
        assertNotNull(repository.decoder(Js5Prot.GROUP_REQUEST_PREFETCH.opcode), "prefetch group-request decoder")
        for (opcode in Js5Prot.CONTROL_OPCODES) {
            assertNotNull(repository.decoder(opcode), "control decoder for opcode $opcode")
        }
        assertNull(repository.decoder(999))
    }

    @Test fun `every domain's encoder is present, keyed by message type`() {
        assertNotNull(repository.encoder(Js5GroupResponse::class.java), "js5 group-response encoder")
        assertNotNull(repository.encoder(ServerSessionKey::class.java), "login server-session-key encoder")
        assertNotNull(repository.encoder(LoginResponse::class.java), "login response encoder")
        assertNotNull(repository.encoder(RebuildLogin::class.java), "game login rebuild encoder")
        assertNotNull(repository.encoder(RebuildNormal::class.java), "game rebuild-normal encoder")
        assertNotNull(repository.encoder(PlayerInfo::class.java), "game player-info encoder")
        assertNotNull(repository.encoder(Logout::class.java), "game logout encoder")
        assertNotNull(repository.decoder(GameClientProt.IF_BUTTONX.opcode), "game if-button-x decoder")
        assertNotNull(repository.decoder(GameClientProt.MESSAGE_PUBLIC.opcode), "game public-chat decoder")
        assertNotNull(repository.decoder(GameClientProt.SET_CHAT_FILTER_SETTINGS.opcode), "game chat-filter decoder")
    }

    @Test fun `collected decoder count matches the number of opcodes every domain module declares`() {
        // Explicit opcodes expose missing or colliding qualified decoder bindings.
        val expectedOpcodes = listOf(Js5Prot.GROUP_REQUEST.opcode, Js5Prot.GROUP_REQUEST_PREFETCH.opcode) +
            Js5Prot.CONTROL_OPCODES.toList() +
            listOf(
                GameClientProt.MOVE_GAMECLICK.opcode,
                GameClientProt.IF_BUTTONX.opcode,
                GameClientProt.MESSAGE_PUBLIC.opcode,
                GameClientProt.SET_CHAT_FILTER_SETTINGS.opcode,
            )
        for (opcode in expectedOpcodes) {
            assertNotNull(repository.decoder(opcode))
        }
        assertEquals(11, expectedOpcodes.size)
    }
}
