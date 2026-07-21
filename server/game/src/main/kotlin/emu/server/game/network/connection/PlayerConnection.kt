package emu.server.game.network.connection

import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.PlayerAction
import emu.server.game.network.output.GameOutputBatch
import emu.server.game.network.output.GameOutputSink
import emu.server.game.network.output.playerinfo.PlayerAppearanceOutput
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
    val appearanceOutput: PlayerAppearanceOutput,
    val attachment: WorldAttachment,
) {
    val publicChat = PlayerPublicChatState()
    val playerInfo = PlayerInfoState(playerIndex)
    private val gameMessages = ArrayDeque<String>(MAX_PENDING_GAME_MESSAGES)
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

    fun queueGameMessage(text: String): Boolean {
        if (gameMessages.size >= MAX_PENDING_GAME_MESSAGES) return false
        gameMessages.addLast(text)
        return true
    }

    fun drainGameMessages(): List<String> {
        if (gameMessages.isEmpty()) return emptyList()
        val drained = ArrayList<String>(gameMessages.size)
        while (gameMessages.isNotEmpty()) drained += gameMessages.removeFirst()
        return drained
    }

    private companion object {
        const val MAX_PENDING_GAME_MESSAGES = 8
    }
}
