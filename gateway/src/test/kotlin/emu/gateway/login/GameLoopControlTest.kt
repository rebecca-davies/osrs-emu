package emu.gateway.login

import emu.crypto.NopStreamCipher
import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PlayerMovement
import emu.game.pathfinding.PlayerRouteRequestQueue
import emu.game.pathfinding.Tile
import emu.game.ui.ButtonClick
import emu.game.ui.PlayerButtonQueue
import emu.gateway.game.PlayerSessionControl
import emu.gateway.game.GameOutboundWriter
import emu.gateway.game.GameOutputBatch
import emu.gateway.game.GameOutputSink
import emu.gateway.game.playerButtonActions
import emu.netcore.pipeline.OutboundSession
import emu.protocol.osrs239.game.buildGameCodecRepository
import emu.protocol.osrs239.game.prot.GameServerProt
import emu.gateway.world.WorldParticipantResult
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
        val buttons = PlayerButtonQueue()
        val control = PlayerSessionControl()

        val result = GameLoop(
            playerId = 1,
            output = GameOutputSink { false },
            playerMovement = movement,
            routeRequests = PlayerRouteRequestQueue(),
            buttonClicks = buttons,
            buttonActions = playerButtonActions(movement, varps, control),
            playerVarps = varps,
            sessionControl = control,
        ).cycle(worldTick = 0)

        assertEquals(WorldParticipantResult.REMOVE, result)
    }

    @Test fun `logout button sends clean logout packet and ends the cycle loop`() = runBlocking {
        val output = ByteChannel(autoFlush = true)
        val codecs = buildGameCodecRepository()
        val varps = initialPlayerVarps().apply { markClientSynchronized() }
        val movement = PlayerMovement(Tile(3222, 3218), OpenCollisionMap)
        val buttons = PlayerButtonQueue()
        val control = PlayerSessionControl()
        val actions = playerButtonActions(movement, varps, control)
        val batches = mutableListOf<GameOutputBatch>()
        val loop =
            GameLoop(
                playerId = 1,
                output = GameOutputSink { batches += it; true },
                playerMovement = movement,
                routeRequests = PlayerRouteRequestQueue(),
                buttonClicks = buttons,
                buttonActions = actions,
                playerVarps = varps,
                sessionControl = control,
            )
        buttons.submit(ButtonClick(182, 8, -1, -1, 1))

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
        val buttons = PlayerButtonQueue()
        val control = PlayerSessionControl()
        val actions = playerButtonActions(movement, varps, control)
        val batches = mutableListOf<GameOutputBatch>()
        val loop =
            GameLoop(
                playerId = 1,
                output = GameOutputSink { batches += it; true },
                playerMovement = movement,
                routeRequests = PlayerRouteRequestQueue(),
                buttonClicks = buttons,
                buttonActions = actions,
                playerVarps = varps,
                sessionControl = control,
            )
        buttons.submit(ButtonClick(160, 28, -1, -1, 1))

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
