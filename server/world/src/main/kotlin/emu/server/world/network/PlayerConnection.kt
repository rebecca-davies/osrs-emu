package emu.server.world.network

import emu.game.action.GameInputQueue
import emu.game.action.PlayerAction
import emu.protocol.osrs239.game.message.PlayerAppearance
import emu.server.session.GameSessionToken
import emu.server.world.runtime.WorldAttachment

/** Bounded transport and protocol state owned by one attached game connection. */
internal class PlayerConnection(
    val token: GameSessionToken,
    val playerIndex: Int,
    val actions: GameInputQueue,
    val output: GameOutputSink,
    displayName: String,
    val attachment: WorldAttachment,
) {
    val appearance = PlayerAppearance(name = displayName)
    val publicChat = PlayerPublicChatState()
    val playerInfo = PlayerInfoState(playerIndex)
    var pendingOutput: GameOutputBatch? = null
    var pendingRoute: PlayerAction.Route? = null
    var logoutPublished: Boolean = false
    var isConnected: Boolean = true
        private set
    private var logoutOutputFailures: Int = 0

    fun disconnect() {
        isConnected = false
    }

    fun recordLogoutOutputFailure(): Int = ++logoutOutputFailures
}
