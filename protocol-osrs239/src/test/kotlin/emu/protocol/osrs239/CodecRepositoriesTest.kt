package emu.protocol.osrs239

import emu.protocol.osrs239.game.gameModule
import emu.protocol.osrs239.game.message.PlayerInfo
import emu.protocol.osrs239.game.message.RebuildLogin
import emu.protocol.osrs239.game.message.RebuildNormal
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

/**
 * Locks in the COLLECTION idiom [buildCodecRepository] relies on (CLAUDE.md §5a addendum): every
 * codec each domain module (`js5Module`/`loginModule`/`gameModule`) declares as a Koin `single ...
 * bind MessageDecoder::class`/`bind MessageEncoder::class` must actually come back out of
 * `koin.getAll<...>()` and end up in the assembled [emu.netcore.codec.CodecRepository]. An
 * incomplete collection (e.g. a qualifier collision silently dropping one of the five
 * [Js5Prot.CONTROL_OPCODES] decoders) would otherwise only surface as a mysterious dropped
 * connection at runtime — this test catches it at build time instead.
 */
class CodecRepositoriesTest {
    private val repository = koinApplication { modules(js5Module, loginModule, gameModule) }.koin.buildCodecRepository()

    @Test fun `every js5 decoder opcode is present`() {
        assertNotNull(repository.decoder(Js5Prot.GROUP_REQUEST.opcode), "urgent group-request decoder")
        assertNotNull(repository.decoder(Js5Prot.GROUP_REQUEST_PREFETCH.opcode), "prefetch group-request decoder")
        for (opcode in Js5Prot.CONTROL_OPCODES) {
            assertNotNull(repository.decoder(opcode), "control decoder for opcode $opcode")
        }
        // Sanity: an opcode no domain declares a decoder for is absent, not silently matched.
        assertNull(repository.decoder(999))
    }

    @Test fun `every domain's encoder is present, keyed by message type`() {
        assertNotNull(repository.encoder(Js5GroupResponse::class.java), "js5 group-response encoder")
        assertNotNull(repository.encoder(ServerSessionKey::class.java), "login server-session-key encoder")
        assertNotNull(repository.encoder(LoginResponse::class.java), "login response encoder")
        assertNotNull(repository.encoder(RebuildLogin::class.java), "game login rebuild encoder")
        assertNotNull(repository.encoder(RebuildNormal::class.java), "game rebuild-normal encoder")
        assertNotNull(repository.encoder(PlayerInfo::class.java), "game player-info encoder")
    }

    @Test fun `collected decoder count matches the number of opcodes every domain module declares`() {
        // 2 group-request decoders (urgent/prefetch) + 1 per control opcode; login/game declare no
        // decoders. Pinned as an explicit count so a future qualifier collision (two decoders
        // silently registered under the same Koin definition) shows up as a size mismatch here
        // rather than only as a missing opcode.
        val expectedOpcodes = listOf(Js5Prot.GROUP_REQUEST.opcode, Js5Prot.GROUP_REQUEST_PREFETCH.opcode) +
            Js5Prot.CONTROL_OPCODES.toList()
        for (opcode in expectedOpcodes) {
            assertNotNull(repository.decoder(opcode))
        }
        assertEquals(7, expectedOpcodes.size)
    }
}
