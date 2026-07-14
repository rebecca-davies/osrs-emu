package emu.gateway.login

import emu.crypto.NopStreamCipher
import emu.game.map.PlayerBuildArea
import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PlayerMovement
import emu.game.pathfinding.PlayerRouteRequestQueue
import emu.game.pathfinding.Tile
import emu.netcore.pipeline.OutboundSession
import emu.protocol.osrs239.buildCodecRepository
import emu.protocol.osrs239.game.gameModule
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readFully
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlinx.coroutines.runBlocking
import org.koin.dsl.koinApplication
import kotlin.time.Duration.Companion.milliseconds

class GameLoopMapRebuildTest {
    @Test
    fun `crossing the scene boundary sends the six byte rebuild before the world update group`() = runBlocking {
        val codecs = koinApplication { modules(gameModule) }.koin.buildCodecRepository()
        val output = ByteChannel(autoFlush = true)
        val movement = PlayerMovement(Tile(3255, 3218), OpenCollisionMap)
        val routeRequests = PlayerRouteRequestQueue()
        val buildArea = PlayerBuildArea(Tile(3222, 3218))
        routeRequests.submit(3256, 3218, 0)

        GameLoop(
            session = OutboundSession(codecs, NopStreamCipher, output),
            tickInterval = 1.milliseconds,
            playerMovement = movement,
            routeRequests = routeRequests,
            buildArea = buildArea,
        ).run(maxTicks = 1)

        val rebuildPacket = ByteArray(9)
        output.readFully(rebuildPacket)
        assertContentEquals(
            byteArrayOf(49, 0, 6, 0, 0, 1, 18, 1, 23),
            rebuildPacket,
        )
    }
}
