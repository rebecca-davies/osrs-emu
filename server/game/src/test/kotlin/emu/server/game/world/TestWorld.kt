package emu.server.game.world

import emu.game.action.IncomingPlayerActionQueue
import emu.game.content.player.login.LoginNotices
import emu.game.content.ui.config.UiContentCatalog
import emu.game.map.GameMap
import emu.game.npc.NpcList
import emu.game.pathfinding.collision.OpenCollisionMap
import emu.game.player.Player
import emu.persistence.character.model.CharacterRecord
import emu.server.game.network.output.GameOutputSink
import emu.server.game.world.entry.PlayerCapacity
import emu.server.game.world.entry.WorldAttachment
import emu.server.session.account.AccountPrivilege
import emu.server.session.handoff.GameSessionToken
import emu.server.session.handoff.ReservationDecision

internal fun testWorld(
    maxPlayerIndex: Int = PlayerCapacity.PER_WORLD,
    gameMap: GameMap = GameMap(OpenCollisionMap),
    npcs: NpcList = NpcList(),
    sessionStartedNanos: () -> Long = System::nanoTime,
): World =
    World(
        gameMap,
        UiContentCatalog.load().gameframe,
        LoginNotices.ALL,
        npcs,
        maxPlayerIndex,
        sessionStartedNanos,
    )

internal fun World.addTestPlayer(
    record: CharacterRecord,
    actions: IncomingPlayerActionQueue,
    output: GameOutputSink,
    privilege: AccountPrivilege = AccountPrivilege.PLAYER,
    sessionToken: GameSessionToken = GameSessionToken("test-${record.id}"),
): Player {
    val reservation = reserve(record.id, sessionToken)
    check(reservation is ReservationDecision.Accepted) { "test player reservation failed: $reservation" }
    val attachment = WorldAttachment()
    stageLogin(sessionToken, record, privilege, actions, output, attachment)
    enterPendingPlayers()
    return allPlayers().single { session(it).token == sessionToken }
}

internal fun World.activateTestPlayer(token: GameSessionToken) {
    requestActivation(token)
    val player = checkNotNull(nextPendingActivation()) { "test player activation was not pending" }
    activate(player) { }
}
