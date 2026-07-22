package emu.server.game.network.connection

import emu.game.action.IncomingPlayerActionQueue
import emu.server.game.network.output.GameOutputBatch
import emu.server.game.network.output.GameOutputSink
import emu.server.game.network.output.npcinfo.NpcInfoState
import emu.server.game.network.output.playerinfo.PlayerAppearanceOutput
import emu.server.game.network.output.playerinfo.PlayerInfoState
import emu.server.game.world.entry.WorldAttachment
import emu.server.session.handoff.GameSessionToken

/** Authenticated game-protocol state retained for one attached connection lifetime. */
internal class GameSession(
    val token: GameSessionToken,
    playerIndex: Int,
    val actions: IncomingPlayerActionQueue,
    val output: GameOutputSink,
    val appearance: PlayerAppearanceOutput,
    val attachment: WorldAttachment,
) {
    val playerInfo = PlayerInfoState(playerIndex)
    val npcInfo = NpcInfoState()
    var pendingOutput: GameOutputBatch? = null
    var logoutPublished: Boolean = false
    var isConnected: Boolean = true
        private set
    private var logoutOutputFailures: Int = 0

    fun disconnect() {
        isConnected = false
    }

    fun recordLogoutOutputFailure(): Int = ++logoutOutputFailures
}
