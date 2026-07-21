package emu.server.game.world

import emu.game.action.IncomingPlayerActionQueue
import emu.game.content.player.login.LoginNotice
import emu.game.content.ui.gameframe.Gameframe
import emu.game.cycle.CycleProfileSnapshot
import emu.persistence.character.model.CharacterRecord
import emu.server.game.network.connection.PlayerConnection
import emu.server.game.network.output.GameOutputSink
import emu.server.game.network.output.login.InitialPlayerOutput
import emu.server.game.network.output.playerinfo.PlayerAppearanceOutput
import emu.server.game.persistence.PlayerWriteBack
import emu.server.game.world.entry.PlayerCapacity
import emu.server.game.world.entry.WorldAttachment
import emu.server.game.world.entry.WorldLogin
import emu.server.game.world.player.ConnectedPlayer
import emu.server.game.world.player.WorldPlayer
import emu.server.session.account.AccountPrivilege
import emu.server.session.handoff.GameSessionToken
import emu.server.session.handoff.ReservationDecision
import emu.server.session.handoff.ReservationRejection
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/** Authoritative player membership, reservations, and indexes owned by the world thread. */
class World(
    private val gameframe: Gameframe,
    loginNotices: List<LoginNotice>,
    maxPlayerIndex: Int = PlayerCapacity.PER_WORLD,
    private val sessionStartedNanos: () -> Long = System::nanoTime,
) {
    private val initialPlayerOutput = InitialPlayerOutput(gameframe, loginNotices)
    private val indexes = PlayerIndexAllocator(maxPlayerIndex)
    private val players = linkedMapOf<Long, ConnectedPlayer>()
    private val playersByToken = mutableMapOf<GameSessionToken, ConnectedPlayer>()
    private val occupiedPlayerIds = mutableSetOf<Long>()
    private val reservations = linkedMapOf<GameSessionToken, Reservation>()
    private val pendingLogins = linkedMapOf<GameSessionToken, PendingLogin>()
    private val pendingActivations = linkedSetOf<GameSessionToken>()

    internal var cycleProfile: CycleProfileSnapshot? = null
        private set

    internal fun reserve(playerId: Long, token: GameSessionToken): ReservationDecision {
        if (
            containsIdentity(playerId) ||
                reservations.containsKey(token) ||
                pendingLogins.containsKey(token) ||
                playersByToken.containsKey(token)
        ) {
            return ReservationDecision.Rejected(ReservationRejection.DUPLICATE)
        }
        val playerIndex = indexes.allocate()
            ?: return ReservationDecision.Rejected(ReservationRejection.CAPACITY)
        check(occupiedPlayerIds.add(playerId)) { "player id was occupied during reservation" }
        reservations[token] = Reservation(playerId, playerIndex)
        return ReservationDecision.Accepted(token, playerIndex)
    }

    internal fun stageLogin(
        token: GameSessionToken,
        record: CharacterRecord,
        privilege: AccountPrivilege,
        actions: IncomingPlayerActionQueue,
        output: GameOutputSink,
        attachment: WorldAttachment,
    ) {
        val reservation = reservations.remove(token)
        if (reservation == null) {
            attachment.reject()
            return
        }
        if (reservation.playerId != record.id || players.containsKey(record.id)) {
            release(reservation)
            attachment.reject()
            return
        }
        pendingLogins[token] =
            PendingLogin(token, reservation, record, privilege, actions, output, attachment)
    }

    internal fun enterPendingPlayers() {
        while (pendingLogins.isNotEmpty()) {
            val entry = pendingLogins.entries.first()
            pendingLogins.remove(entry.key)
            enterPlayer(entry.value)
        }
    }

    private fun enterPlayer(pending: PendingLogin) {
        try {
            val player = WorldPlayer(pending.record, pending.privilege)
            val appearanceOutput = PlayerAppearanceOutput(player)
            val connection =
                PlayerConnection(
                    token = pending.sessionToken,
                    playerIndex = pending.reservation.playerIndex,
                    actions = pending.actions,
                    output = pending.output,
                    appearanceOutput = appearanceOutput,
                    attachment = pending.attachment,
                )
            val connected =
                ConnectedPlayer(
                    player,
                    connection,
                    PlayerWriteBack(pending.record, sessionStartedNanos()),
                )
            val login =
                WorldLogin(
                    connection.playerIndex,
                    initialPlayerOutput.build(
                        player,
                        connection.playerIndex,
                        appearanceOutput.message(player),
                    ),
                )
            players[player.id] = connected
            playersByToken[connection.token] = connected
            connection.attachment.attach(login)
        } catch (failure: Exception) {
            release(pending.reservation)
            pending.attachment.reject()
            logger.error(failure) {
                "world: rejected staged player ${pending.record.id} during construction"
            }
        }
    }

    internal fun release(token: GameSessionToken) {
        reservations.remove(token)?.let(::release)
        pendingLogins.remove(token)?.let { pending ->
            release(pending.reservation)
            pending.attachment.reject()
        }
    }

    internal fun requestActivation(token: GameSessionToken) {
        if (playersByToken.containsKey(token)) pendingActivations += token
    }

    internal fun nextPendingActivation(): ConnectedPlayer? {
        while (pendingActivations.isNotEmpty()) {
            val token = pendingActivations.first()
            pendingActivations.remove(token)
            return playersByToken[token] ?: continue
        }
        return null
    }

    internal fun activate(
        connected: ConnectedPlayer,
        beforeActivation: (WorldPlayer) -> Unit,
    ) {
        if (playersByToken[connected.connection.token] !== connected) return
        val player = connected.player
        player.activate(gameframe) { beforeActivation(player) }
    }

    internal fun requestLogout(token: GameSessionToken) {
        playersByToken[token]?.player?.requestLogout()
    }

    internal fun disconnect(token: GameSessionToken) {
        playersByToken[token]?.let { connected ->
            connected.connection.disconnect()
            connected.player.requestLogout()
        }
    }

    internal fun requestAllLogouts() {
        players.values.forEach {
            it.player.enableShutdownAccess()
            it.player.requestLogout()
        }
    }

    internal fun recordCycleProfile(snapshot: CycleProfileSnapshot) {
        cycleProfile = snapshot
    }

    internal fun clearCycleProfile() {
        cycleProfile = null
    }

    internal fun activePlayers(): List<ConnectedPlayer> =
        buildList { collectActivePlayers(this) }

    internal fun collectActivePlayers(destination: MutableCollection<ConnectedPlayer>) {
        for (connected in players.values) {
            if (connected.player.active && !connected.player.loggingOut) destination += connected
        }
    }

    internal fun collectCyclePlayers(destination: MutableCollection<ConnectedPlayer>) {
        for (connected in players.values) {
            if (
                !connected.writeBack.snapshotTaken &&
                    (connected.player.active ||
                        connected.player.logoutRequested ||
                        connected.player.loggingOut)
            ) {
                destination += connected
            }
        }
    }

    internal fun allPlayers(): List<ConnectedPlayer> = players.values.toList()

    internal fun collectAllPlayers(destination: MutableCollection<ConnectedPlayer>) {
        for (connected in players.values) destination += connected
    }

    internal fun isEmpty(): Boolean = players.isEmpty()

    internal fun contains(playerId: Long): Boolean = players.containsKey(playerId)

    internal fun remove(connected: ConnectedPlayer) {
        val player = connected.player
        val connection = connected.connection
        if (players.remove(player.id) !== connected) return
        playersByToken.remove(connection.token, connected)
        pendingActivations.remove(connection.token)
        indexes.release(connection.playerIndex)
        occupiedPlayerIds.remove(player.id)
        connection.attachment.remove()
    }

    internal fun rejectPendingLogins() {
        reservations.values.forEach {
            release(it)
        }
        reservations.clear()
        pendingLogins.values.forEach { pending ->
            release(pending.reservation)
            pending.attachment.reject()
        }
        pendingLogins.clear()
    }

    private fun containsIdentity(playerId: Long): Boolean = playerId in occupiedPlayerIds

    private fun release(reservation: Reservation) {
        indexes.release(reservation.playerIndex)
        occupiedPlayerIds.remove(reservation.playerId)
    }

    private data class Reservation(val playerId: Long, val playerIndex: Int)

    private data class PendingLogin(
        val sessionToken: GameSessionToken,
        val reservation: Reservation,
        val record: CharacterRecord,
        val privilege: AccountPrivilege,
        val actions: IncomingPlayerActionQueue,
        val output: GameOutputSink,
        val attachment: WorldAttachment,
    )
}
