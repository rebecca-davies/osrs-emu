package emu.server.world.session

import emu.compression.HuffmanCodec
import emu.game.chat.chatActions
import emu.game.input.PlayerInput
import emu.game.input.PlayerInputQueue
import emu.game.input.PlayerInputQueueConfig
import emu.game.pathfinding.OpenCollisionMap
import emu.game.pathfinding.PlayerMovement
import emu.game.pathfinding.Tile
import emu.game.ui.ButtonClick
import emu.game.ui.buttonActions
import emu.server.world.network.GameOutputSink
import emu.server.world.network.GameConnection
import emu.server.world.network.handler.MessagePublicHandler
import emu.protocol.osrs239.game.message.MessagePublic
import emu.server.world.player.PlayerSessionControl
import emu.server.world.player.playerButtonActions
import emu.transport.message.OutgoingMessage
import emu.transport.pipeline.HandlerContext
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameLoopInputTest {
    @Test
    fun `mixed inputs execute in order before latest route and movement`() = runBlocking {
        val movement = PlayerMovement(Tile(0, 0), OpenCollisionMap)
        val inputs = PlayerInputQueue(PlayerInputQueueConfig())
        val varps = initialPlayerVarps().apply { markClientSynchronized() }
        val control = PlayerSessionControl()
        val executed = mutableListOf<String>()
        val huffman = HuffmanCodec(ByteArray(256) { 8 })
        val chatActions = chatActions { onPublicMessage { executed += "chat" } }
        val buttonActions = buttonActions {
            onButton(160, 28) {
                executed += "button"
                movement.runEnabled = true
            }
        }

        MessagePublicHandler(huffman, inputs).handle(
            MessagePublic(0, 0, 0, huffman.encode("hello")),
            NoOutput,
        )
        assertTrue(executed.isEmpty(), "network IO must not execute chat content")
        inputs.submit(PlayerInput.Route(0, 3, invertRun = false))
        inputs.submit(PlayerInput.Button(ButtonClick(160, 28, -1, -1, 1)))
        inputs.submit(PlayerInput.Route(3, 0, invertRun = false))

        GameLoop(
            playerId = 1,
            connection = GameConnection(inputs, GameOutputSink { true }),
            playerMovement = movement,
            buttonActions = buttonActions,
            playerVarps = varps,
            chatActions = chatActions,
            sessionControl = control,
        ).cycle(worldTick = 0)

        assertEquals(listOf("chat", "button"), executed)
        assertTrue(movement.runEnabled)
        assertEquals(Tile(2, 0), movement.position)
    }

    @Test
    fun `route uses authoritative plane and control temporarily reverses run mode`() {
        val movement = PlayerMovement(Tile(0, 0, plane = 2), OpenCollisionMap)
        val inputs = PlayerInputQueue(PlayerInputQueueConfig())
        val varps = initialPlayerVarps().apply { markClientSynchronized() }
        val control = PlayerSessionControl()
        inputs.submit(PlayerInput.Route(3, 0, invertRun = true))

        GameLoop(
            playerId = 1,
            connection = GameConnection(inputs, GameOutputSink { true }),
            playerMovement = movement,
            buttonActions = playerButtonActions(movement, varps, control),
            playerVarps = varps,
            sessionControl = control,
        ).cycle(worldTick = 0)

        assertFalse(movement.runEnabled)
        assertEquals(Tile(2, 0, plane = 2), movement.position)
    }

    private object NoOutput : HandlerContext {
        override suspend fun write(message: OutgoingMessage) =
            error("player input must not write from network IO")
    }
}
