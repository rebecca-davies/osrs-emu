package emu.gateway.login

import emu.crypto.IsaacCipher
import emu.game.cycle.GameCycle
import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PlayerMovement
import emu.game.pathfinding.PlayerRouteRequestQueue
import emu.game.pathfinding.Tile
import emu.game.ui.PlayerButtonQueue
import emu.protocol.osrs239.buildCodecRepository
import emu.protocol.osrs239.game.gameModule
import emu.protocol.osrs239.game.prot.GameClientProt
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeByte
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class GameInboundTest {
    @Test
    fun `unsupported packet is framed before move click without losing ISAAC alignment`() = runBlocking {
        val seeds = intArrayOf(11, 22, 33, 44)
        val clientCipher = IsaacCipher(seeds)
        val serverCipher = IsaacCipher(seeds)
        val codecs = koinApplication { modules(gameModule) }.koin.buildCodecRepository()
        val input = ByteChannel(autoFlush = true)
        val unusedOutput = ByteChannel(autoFlush = true)
        val requests = PlayerRouteRequestQueue()
        val movement = PlayerMovement(Tile(3222, 3218), OpenCollisionMap)
        val job = launch {
            drainGameInbound(input, unusedOutput, serverCipher, codecs, requests, PlayerButtonQueue(), 2.seconds)
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

        GameCycle(requests.cycleProcesses(movement) + movement.cycleProcesses()).tick()

        assertEquals(Tile(3223, 3218), movement.position)
    }

    private suspend fun ByteChannel.writeEncryptedOpcode(opcode: Int, cipher: IsaacCipher) {
        writeByte(((opcode + cipher.nextInt()) and 0xFF).toByte())
    }
}
