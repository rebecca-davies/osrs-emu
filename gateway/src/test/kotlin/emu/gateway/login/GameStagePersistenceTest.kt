package emu.gateway.login

import emu.crypto.IsaacCipher
import emu.gateway.world.WorldRuntime
import emu.persistence.PlayerPosition
import emu.persistence.PlayerRecord
import emu.protocol.osrs239.buildCodecRepository
import emu.protocol.osrs239.game.gameModule
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class GameStagePersistenceTest {
    @Test
    fun `restores the persisted tile and flushes it once when the session exits`() = runBlocking {
        val codecs = koinApplication { modules(gameModule) }.koin.buildCodecRepository()
        val player =
            PlayerRecord(
                id = 42,
                username = "rebecca bird",
                displayName = "Rebecca_Bird",
                position = PlayerPosition(3205, 3206, 1),
                playTimeSeconds = 123,
            )
        val inbound = ByteChannel()
        val outbound = ByteChannel(autoFlush = true)
        val sink = launch {
            val buffer = ByteArray(8192)
            while (outbound.readAvailable(buffer) != -1) {
                // Drain encoded packets so the writer can never back-pressure this unit test.
            }
        }
        data class Save(val id: Long, val position: PlayerPosition, val seconds: Long, val varps: Map<Int, Int>)
        val saves = mutableListOf<Save>()
        val worldRuntime = WorldRuntime(tickInterval = 1.milliseconds)
        val worldJob = launch { worldRuntime.run() }

        runGameStage(
            read = inbound,
            write = outbound,
            inboundCipher = IsaacCipher(intArrayOf(1, 2, 3, 4)),
            outboundCipher = IsaacCipher(intArrayOf(51, 52, 53, 54)),
            gameCodecs = codecs,
            player = player,
            worldRuntime = worldRuntime,
            saveSession = { id, position, seconds, varps -> saves += Save(id, position, seconds, varps) },
            idleTimeout = 1.seconds,
            maxTicks = 0,
        )
        worldJob.cancelAndJoin()

        outbound.close()
        sink.join()
        assertEquals(1, saves.size)
        assertEquals(42, saves.single().id)
        assertEquals(player.position, saves.single().position)
        assertTrue(saves.single().seconds >= 0)
        assertEquals(emptyMap(), saves.single().varps)
    }
}
