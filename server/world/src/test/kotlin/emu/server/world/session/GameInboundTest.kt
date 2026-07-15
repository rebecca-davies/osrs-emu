package emu.server.world.session

import emu.crypto.IsaacCipher
import emu.game.input.PlayerInput
import emu.game.input.PlayerInputQueue
import emu.game.input.PlayerInputQueueConfig
import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PlayerMovement
import emu.game.pathfinding.Tile
import emu.protocol.osrs239.game.buildGameCodecRepository
import emu.protocol.osrs239.game.prot.GameClientProt
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeByte
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class GameInboundTest {
    @Test
    fun `unsupported packet is framed before move click without losing ISAAC alignment`() = runBlocking {
        val seeds = intArrayOf(11, 22, 33, 44)
        val clientCipher = IsaacCipher(seeds)
        val serverCipher = IsaacCipher(seeds)
        val codecs = buildGameCodecRepository()
        val input = ByteChannel(autoFlush = true)
        val inputs = PlayerInputQueue(PlayerInputQueueConfig())
        val movement = PlayerMovement(Tile(3222, 3218), OpenCollisionMap)
        val job = launch {
            drainGameInbound(input, { true }, serverCipher, codecs, inputs, 2.seconds)
        }

        input.writeEncryptedOpcode(0, clientCipher)
        input.writeByte(0xAA.toByte())
        input.writeByte(0xBB.toByte())
        input.writeEncryptedOpcode(GameClientProt.MOVE_GAMECLICK.opcode, clientCipher)
        input.writeByte(5)
        input.writeByte(0x98.toByte())
        input.writeByte(0x0C)
        input.writeByte(0x92.toByte())
        input.writeByte(0x0C)
        input.writeByte(0x80.toByte())
        input.close()
        job.join()

        val route = inputs.drainToList().single() as PlayerInput.Route
        movement.routeTo(Tile(route.x, route.y, movement.position.plane))
        movement.process()

        assertEquals(Tile(3223, 3218), movement.position)
    }

    private suspend fun ByteChannel.writeEncryptedOpcode(opcode: Int, cipher: IsaacCipher) {
        writeByte(((opcode + cipher.nextInt()) and 0xFF).toByte())
    }

    private fun PlayerInputQueue.drainToList(): List<PlayerInput> =
        buildList { drain(::add) }
}
