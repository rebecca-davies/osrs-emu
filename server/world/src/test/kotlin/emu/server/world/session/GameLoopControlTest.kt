package emu.server.world.session

import emu.crypto.NopStreamCipher
import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PlayerMovement
import emu.game.input.PlayerInput
import emu.game.input.PlayerInputQueue
import emu.game.input.PlayerInputQueueConfig
import emu.game.pathfinding.Tile
import emu.game.ui.ButtonClick
import emu.server.world.player.PlayerLogoutState
import emu.server.world.network.GameOutboundWriter
import emu.server.world.network.GameOutputBatch
import emu.server.world.network.GameOutputSink
import emu.server.world.network.GameConnection
import emu.server.world.player.playerButtonActions
import emu.transport.pipeline.OutboundSession
import emu.protocol.osrs239.game.buildGameCodecRepository
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.server.world.runtime.WorldParticipantResult
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class GameLoopControlTest {
    @Test fun `outbound saturation removes only that world participant`() = runBlocking {
        val varps = initialPlayerVarps().apply { markClientSynchronized() }
        val movement = PlayerMovement(Tile(3222, 3218), OpenCollisionMap)
        val inputs = PlayerInputQueue(PlayerInputQueueConfig())
        val control = PlayerLogoutState()

        val result = GameLoop(
            playerId = 1,
            connection = GameConnection(inputs, GameOutputSink { false }),
            playerMovement = movement,
            buttonActions = playerButtonActions(movement, varps, control),
            playerVarps = varps,
            logout = control,
        ).cycle(worldTick = 0)

        assertEquals(WorldParticipantResult.REMOVE, result)
    }

    @Test fun `logout button sends clean logout packet and ends the cycle loop`() = runBlocking {
        val output = ByteChannel(autoFlush = true)
        val codecs = buildGameCodecRepository()
        val varps = initialPlayerVarps().apply { markClientSynchronized() }
        val movement = PlayerMovement(Tile(3222, 3218), OpenCollisionMap)
        val inputs = PlayerInputQueue(PlayerInputQueueConfig())
        val control = PlayerLogoutState()
        val actions = playerButtonActions(movement, varps, control)
        val batches = mutableListOf<GameOutputBatch>()
        val loop =
            GameLoop(
                playerId = 1,
                connection = GameConnection(inputs, GameOutputSink { batches += it; true }),
                playerMovement = movement,
                buttonActions = actions,
                playerVarps = varps,
                logout = control,
            )
        inputs.submit(PlayerInput.Button(ButtonClick(182, 8, -1, -1, 1)))

        loop.cycle(worldTick = 0)
        GameOutboundWriter(OutboundSession(codecs, NopStreamCipher, output)).write(batches.single())
        output.close()

        assertEquals(
            listOf(GameServerProt.LOGOUT.opcode.toByte()),
            output.readRemaining().readByteArray().toList(),
        )
    }

    @Test fun `run button publishes the changed account varp before world output`() = runBlocking {
        val output = ByteChannel(autoFlush = true)
        val codecs = buildGameCodecRepository()
        val varps = initialPlayerVarps().apply { markClientSynchronized() }
        val movement = PlayerMovement(Tile(3222, 3218), OpenCollisionMap)
        val inputs = PlayerInputQueue(PlayerInputQueueConfig())
        val control = PlayerLogoutState()
        val actions = playerButtonActions(movement, varps, control)
        val batches = mutableListOf<GameOutputBatch>()
        val loop =
            GameLoop(
                playerId = 1,
                connection = GameConnection(inputs, GameOutputSink { batches += it; true }),
                playerMovement = movement,
                buttonActions = actions,
                playerVarps = varps,
                logout = control,
            )
        inputs.submit(PlayerInput.Button(ButtonClick(160, 28, -1, -1, 1)))

        loop.cycle(worldTick = 0)
        GameOutboundWriter(OutboundSession(codecs, NopStreamCipher, output)).write(batches.single())
        output.close()
        val wire = output.readRemaining().readByteArray()

        assertEquals(
            listOf(GameServerProt.VARP_SMALL.opcode.toByte(), 129.toByte(), 45, 0),
            wire.take(4),
        )
    }
}
