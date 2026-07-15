package emu.server.game.session

import emu.crypto.IsaacCipher
import emu.server.game.world.WorldParticipant
import emu.server.game.world.WorldParticipantResult
import emu.server.game.world.WorldRuntime
import emu.protocol.osrs239.game.buildGameCodecRepository
import emu.protocol.osrs239.game.prot.GameServerProt
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readFully
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val OTHER_PLAYER_SLOTS = 2046
private const val GPI_INIT_BITS = 30 + OTHER_PLAYER_SLOTS * 18
private const val GPI_INIT_BYTES = (GPI_INIT_BITS + 7) / 8
private const val ZONE_BYTES = 6
private const val REBUILD_NORMAL_BODY_SIZE = GPI_INIT_BYTES + ZONE_BYTES

private suspend fun readU16(channel: ByteReadChannel): Int {
    val high = channel.readByte().toInt() and 0xFF
    val low = channel.readByte().toInt() and 0xFF
    return (high shl 8) or low
}

private fun decodePacked30(body: ByteArray): Triple<Int, Int, Int> {
    var accumulator = 0L
    for (index in 0 until 4) accumulator = (accumulator shl 8) or (body[index].toLong() and 0xFF)
    val packed = (accumulator ushr 2).toInt()
    return Triple((packed ushr 28) and 0x3, (packed ushr 14) and 0x3FFF, packed and 0x3FFF)
}

class GameStageTest {
    @Test
    fun `game admission writes an ISAAC adjusted rebuild for the persisted tile`() = runBlocking {
        val seeds = intArrayOf(11, 22, 33, 44)
        val input = ByteChannel()
        val output = ByteChannel(autoFlush = true)
        val world = WorldRuntime(tickInterval = 20.milliseconds)
        val occupied =
            world.register(
                object : WorldParticipant {
                    override val playerId: Long = Long.MAX_VALUE

                    override fun cycle(worldTick: Long): WorldParticipantResult = WorldParticipantResult.KEEP
                },
                startActive = false,
            )
        val worldJob = launch { world.run() }
        assertEquals(1, occupied.playerIndex.await())

        val gameJob =
            launch {
                runGameStage(
                    read = input,
                    write = output,
                    inboundCipher = IsaacCipher(seeds),
                    outboundCipher = IsaacCipher(IntArray(seeds.size) { seeds[it] + 50 }),
                    gameCodecs = buildGameCodecRepository(),
                    player = TEST_PLAYER,
                    worldRuntime = world,
                    saveSession = { _, _, _, _ -> },
                    idleTimeout = 2.seconds,
                    maxTicks = 1,
                )
            }

        val oracle = IsaacCipher(IntArray(seeds.size) { seeds[it] + 50 })
        assertEquals(
            (GameServerProt.REBUILD_NORMAL.opcode + oracle.nextInt()) and 0xFF,
            output.readByte().toInt() and 0xFF,
        )
        assertEquals(REBUILD_NORMAL_BODY_SIZE, readU16(output))
        val rebuild = ByteArray(REBUILD_NORMAL_BODY_SIZE).also { output.readFully(it) }
        val (plane, x, y) = decodePacked30(rebuild)
        assertEquals(TEST_PLAYER.position.plane, plane)
        assertEquals(TEST_PLAYER.position.x, x)
        assertEquals(TEST_PLAYER.position.y, y)

        gameJob.join()
        worldJob.cancelAndJoin()
        input.close()
        output.close()
    }
}
