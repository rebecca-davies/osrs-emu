package emu.server.game.network.input

import emu.compression.HuffmanCodec
import emu.crypto.IsaacCipher
import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.IncomingPlayerActionQueueConfig
import emu.game.action.PlayerAction
import emu.protocol.osrs239.game.buildGameCodecRepository
import emu.protocol.osrs239.game.prot.GameClientProt
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeByte
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class GameInboundReaderTest {
    @Test
    fun `unsupported and handled player controls preserve alignment before move click`() = runBlocking {
        val seeds = intArrayOf(11, 22, 33, 44)
        val clientCipher = IsaacCipher(seeds)
        val serverCipher = IsaacCipher(seeds)
        val codecs = buildGameCodecRepository()
        val input = ByteChannel(autoFlush = true)
        val actions = IncomingPlayerActionQueue(IncomingPlayerActionQueueConfig())
        val reader = GameInboundReader(codecs, HuffmanCodec(ByteArray(256) { 8 }), 2.seconds)
        val job = launch {
            reader.run(input, serverCipher, actions)
        }

        input.writeEncryptedOpcode(0, clientCipher)
        input.writeByte(0xAA.toByte())
        input.writeByte(0xBB.toByte())
        input.writeEncryptedOpcode(GameClientProt.CLOSE_MODAL.opcode, clientCipher)
        input.writeEncryptedOpcode(GameClientProt.IDLE.opcode, clientCipher)
        input.writeEncryptedOpcode(GameClientProt.MOVE_GAMECLICK.opcode, clientCipher)
        input.writeByte(5)
        input.writeByte(0x98.toByte())
        input.writeByte(0x0C)
        input.writeByte(0x92.toByte())
        input.writeByte(0x0C)
        input.writeByte(0x80.toByte())
        input.close()
        job.join()

        val queued = actions.drainToList()
        assertEquals(3, queued.size)
        assertEquals(PlayerAction.CloseModal, queued[0])
        assertEquals(PlayerAction.IdleLogout, queued[1])
        val route = queued[2] as PlayerAction.Route
        assertEquals(PlayerAction.Route(3224, 3218), route)
    }

    private suspend fun ByteChannel.writeEncryptedOpcode(opcode: Int, cipher: IsaacCipher) {
        writeByte(((opcode + cipher.nextInt()) and 0xFF).toByte())
    }

    private fun IncomingPlayerActionQueue.drainToList(): List<PlayerAction> =
        buildList { drain(::add) }
}
