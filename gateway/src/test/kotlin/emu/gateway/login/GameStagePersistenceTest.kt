package emu.gateway.login

import emu.crypto.IsaacCipher
import emu.persistence.PlayerPosition
import emu.persistence.PlayerRecord
import emu.protocol.osrs239.buildCodecRepository
import emu.protocol.osrs239.game.gameModule
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
        val saves = mutableListOf<Triple<Long, PlayerPosition, Long>>()

        runGameStage(
            read = inbound,
            write = outbound,
            inboundCipher = IsaacCipher(intArrayOf(1, 2, 3, 4)),
            outboundCipher = IsaacCipher(intArrayOf(51, 52, 53, 54)),
            gameCodecs = codecs,
            player = player,
            saveSession = { id, position, seconds -> saves += Triple(id, position, seconds) },
            idleTimeout = 1.seconds,
            tickInterval = 1.milliseconds,
            maxTicks = 0,
        )

        outbound.close()
        sink.join()
        assertEquals(1, saves.size)
        assertEquals(42, saves.single().first)
        assertEquals(player.position, saves.single().second)
        assertTrue(saves.single().third >= 0)
    }
}
