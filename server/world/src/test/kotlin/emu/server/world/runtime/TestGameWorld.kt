package emu.server.world.runtime

import emu.game.action.GameInputQueue
import emu.game.content.player.login.LoginNotices
import emu.game.content.ui.UiContentCatalog
import emu.persistence.character.PlayerRecord
import emu.server.session.GameSessionToken
import emu.server.session.ReservationDecision
import emu.server.world.network.GameOutputSink

internal fun testGameWorld(
    maxPlayerIndex: Int = PlayerCapacity.PER_WORLD,
    sessionStartedNanos: () -> Long = System::nanoTime,
): GameWorld =
    GameWorld(
        UiContentCatalog.load().gameframe,
        LoginNotices.ALL,
        maxPlayerIndex,
        sessionStartedNanos,
    )

internal fun GameWorld.addTestPlayer(
    record: PlayerRecord,
    actions: GameInputQueue,
    output: GameOutputSink,
    sessionToken: GameSessionToken = GameSessionToken("test-${record.id}"),
): ConnectedPlayer {
    val reservation = reserve(record.id, sessionToken)
    check(reservation is ReservationDecision.Accepted) { "test player reservation failed: $reservation" }
    val attachment = WorldAttachment()
    stageLogin(sessionToken, record, actions, output, attachment)
    enterPendingPlayers()
    return allPlayers().single { it.connection.token == sessionToken }
}

internal fun GameWorld.activateTestPlayer(token: GameSessionToken) {
    requestActivation(token)
    val connected = checkNotNull(nextPendingActivation()) { "test player activation was not pending" }
    activate(connected) { }
}
