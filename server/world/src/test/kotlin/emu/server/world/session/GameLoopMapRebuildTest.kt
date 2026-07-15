package emu.server.world.session

import emu.crypto.NopStreamCipher
import emu.game.input.PlayerInput
import emu.game.input.PlayerInputQueue
import emu.game.input.PlayerInputQueueConfig
import emu.game.map.PlayerBuildArea
import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PlayerMovement
import emu.game.pathfinding.Tile
import emu.server.world.player.PlayerLogoutState
import emu.server.world.network.GameOutboundWriter
import emu.server.world.network.GameOutputBatch
import emu.server.world.network.GameOutputSink
import emu.server.world.network.GameConnection
import emu.server.world.player.playerButtonActions
import emu.transport.pipeline.OutboundSession
import emu.protocol.osrs239.game.buildGameCodecRepository
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readFully
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlinx.coroutines.runBlocking

class GameLoopMapRebuildTest {
    @Test
    fun `crossing the scene boundary sends the six byte rebuild before the world update group`() = runBlocking {
        val codecs = buildGameCodecRepository()
        val output = ByteChannel(autoFlush = true)
        val movement = PlayerMovement(Tile(3255, 3218), OpenCollisionMap)
        val inputs = PlayerInputQueue(PlayerInputQueueConfig())
        val buildArea = PlayerBuildArea(Tile(3222, 3218))
        val playerVarps = initialPlayerVarps().apply { markClientSynchronized() }
        val logout = PlayerLogoutState()
        val batches = mutableListOf<GameOutputBatch>()
        inputs.submit(PlayerInput.Route(3256, 3218, invertRun = false))

        GameLoop(
            playerId = 1,
            connection = GameConnection(inputs, GameOutputSink { batches += it; true }),
            playerMovement = movement,
            buildArea = buildArea,
            buttonActions = playerButtonActions(movement, playerVarps, logout),
            playerVarps = playerVarps,
            logout = logout,
        ).cycle(worldTick = 0)
        GameOutboundWriter(OutboundSession(codecs, NopStreamCipher, output)).write(batches.single())

        val rebuildPacket = ByteArray(9)
        output.readFully(rebuildPacket)
        assertContentEquals(
            byteArrayOf(49, 0, 6, 0, 0, 1, 18, 1, 23),
            rebuildPacket,
        )
    }
}
