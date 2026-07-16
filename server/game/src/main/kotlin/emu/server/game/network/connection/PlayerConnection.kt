package emu.server.game.network.connection

import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.PlayerAction
import emu.protocol.osrs239.game.message.playerinfo.PlayerAppearance
import emu.server.game.network.output.GameOutputBatch
import emu.server.game.network.output.GameOutputSink
import emu.server.game.network.output.playerinfo.PlayerInfoState
import emu.server.game.network.output.playerinfo.PlayerPublicChatState
import emu.server.game.world.entry.WorldAttachment
import emu.server.session.handoff.GameSessionToken

/** Bounded transport and protocol state owned by one attached game connection. */
internal class PlayerConnection(
    val token: GameSessionToken,
    val playerIndex: Int,
    val actions: IncomingPlayerActionQueue,
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
