package emu.server.game.world

import emu.game.action.IncomingPlayerActionQueue
import emu.game.action.PlayerAction
import emu.game.content.player.login.LoginNotice
import emu.game.content.ui.gameframe.Gameframe
import emu.game.cycle.CycleProfileSnapshot
import emu.game.map.GameMap
import emu.game.map.MapInstance
import emu.game.map.Tile
import emu.game.npc.Npc
import emu.game.npc.NpcList
import emu.game.player.Player
import emu.game.player.PlayerChatFilters
import emu.game.player.StaffModLevel
import emu.persistence.character.model.CharacterRecord
import emu.server.game.network.connection.GameSession
import emu.server.game.network.output.GameOutputSink
import emu.server.game.network.output.login.InitialPlayerOutput
import emu.server.game.network.output.playerinfo.PlayerAppearanceOutput
import emu.server.game.persistence.PlayerWriteBack
import emu.server.game.world.entry.PlayerCapacity
import emu.server.game.world.entry.WorldAttachment
import emu.server.game.world.entry.WorldLogin
import emu.server.game.world.player.PlayerList
import emu.server.session.account.AccountPrivilege
import emu.server.session.handoff.GameSessionToken
import emu.server.session.handoff.ReservationDecision
import emu.server.session.handoff.ReservationRejection
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/** Authoritative live-entity membership, player reservations, and map access owned by the world thread. */
class World(
    private val map: GameMap,
    private val gameframe: Gameframe,
    loginNotices: List<LoginNotice>,
    private val npcs: NpcList,
    maxPlayerIndex: Int = PlayerCapacity.PER_WORLD,
    private val sessionStartedNanos: () -> Long = System::nanoTime,
) {
    private val initialPlayerOutput = InitialPlayerOutput(gameframe, loginNotices)
    private val indexes = PlayerIndexAllocator(maxPlayerIndex)
    private val players = PlayerList(maxPlayerIndex)
    private val occupiedPlayerIds = mutableSetOf<Long>()
    private val reservations = linkedMapOf<GameSessionToken, Reservation>()
    private val pendingLogins = linkedMapOf<GameSessionToken, PendingLogin>()
    private val pendingActivations = linkedSetOf<GameSessionToken>()

    internal var cycleProfile: CycleProfileSnapshot? = null
        private set

    internal fun reserve(playerId: Long, token: GameSessionToken): ReservationDecision {
        if (
            playerId in occupiedPlayerIds ||
                reservations.containsKey(token) ||
                pendingLogins.containsKey(token) ||
                players.player(token) != null
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
        if (reservation.playerId != record.id || players.contains(record.id)) {
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
            val record = pending.record
            val player =
                Player(
                    id = record.id,
                    index = pending.reservation.playerIndex,
                    displayName = record.displayName,
                    staffModLevel = StaffModLevel(pending.privilege.level),
                    initialPosition = Tile(record.position.x, record.position.y, record.position.plane),
                    savedVarps = record.varps,
                    initialChatFilters =
                        PlayerChatFilters(
                            record.chatFilters.publicMode,
                            record.chatFilters.privateMode,
                            record.chatFilters.tradeMode,
                        ),
                    initialAppearance = record.appearance,
                )
            val appearance = PlayerAppearanceOutput(player)
            val session =
                GameSession(
                    token = pending.sessionToken,
                    playerIndex = player.index,
                    actions = pending.actions,
                    output = pending.output,
                    appearance = appearance,
                    attachment = pending.attachment,
                )
            val login =
                WorldLogin(
                    player.index,
                    initialPlayerOutput.build(player, appearance.message(player)),
                )
            players.add(
                player,
                session,
                PlayerWriteBack(record, sessionStartedNanos()),
            )
            session.attachment.attach(login)
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
        if (players.player(token) != null) pendingActivations += token
    }

    internal fun nextPendingActivation(): Player? {
        while (pendingActivations.isNotEmpty()) {
            val token = pendingActivations.first()
            pendingActivations.remove(token)
            return players.player(token) ?: continue
        }
        return null
    }

    internal fun activate(
        player: Player,
        beforeActivation: (Player) -> Unit,
    ) {
        if (!players.contains(player)) return
        player.activate(gameframe) { beforeActivation(player) }
    }

    internal fun requestLogout(token: GameSessionToken) {
        players.player(token)?.requestLogout()
    }

    internal fun disconnect(token: GameSessionToken) {
        players.player(token)?.let { player ->
            session(player).disconnect()
            player.requestLogout()
        }
    }

    internal fun requestAllLogouts() {
        players.all().forEach { player ->
            player.enableShutdownAccess()
            player.requestLogout()
        }
    }

    internal fun recordCycleProfile(snapshot: CycleProfileSnapshot) {
        cycleProfile = snapshot
    }

    internal fun clearCycleProfile() {
        cycleProfile = null
    }

    internal fun retryMapAreaRequests() {
        map.retryAreaRequests()
    }

    internal fun prepareCurrentMapArea(player: Player) {
        map.prepareArea(player.movement.position)
    }

    internal fun collectActivePlayers(destination: MutableCollection<Player>) =
        players.collectActive(destination)

    internal fun collectCyclePlayers(destination: MutableCollection<Player>) =
        players.collectCycle(destination)

    internal fun collectAllPlayers(destination: MutableCollection<Player>) =
        players.collectAll(destination)

    internal fun collectNpcs(destination: MutableCollection<Npc>) =
        npcs.collect(destination)

    /** Advances one unpaused targeted NPC by at most one collision-valid tile. */
    internal fun advanceNpc(npc: Npc) {
        val target = npc.target ?: return
        if (
            !players.contains(target) || !target.active || target.loggingOut ||
            target.mapInstance != npc.mapInstance
        ) {
            npc.clearTarget()
            return
        }
        if (npc.paused) return
        val destination = map.nextDumbNpcStep(npc.position, npc.type.size, target.movement.position) ?: return
        if (npcs.intersects(npc.mapInstance, destination, npc.type.size, excluding = npc)) return
        npc.walkTo(destination)
    }

    internal fun removeNpc(npc: Npc): Boolean = npcs.remove(npc)

    internal fun activePlayers(): List<Player> =
        buildList { players.collectActive(this) }

    internal fun allPlayers(): List<Player> = players.all()

    internal fun isEmpty(): Boolean = players.isEmpty

    internal fun contains(playerId: Long): Boolean = players.contains(playerId)

    internal fun session(player: Player): GameSession = players.session(player)

    internal fun writeBack(player: Player): PlayerWriteBack = players.writeBack(player)

    internal fun drainActions(
        player: Player,
        apply: (PlayerAction) -> Unit,
    ): Int = session(player).actions.drain(apply)

    internal fun resolveRoute(player: Player) {
        map.resolveRoute(player)
    }

    internal fun advanceMovement(player: Player) {
        map.advance(player)
    }

    internal fun remove(player: Player) {
        val session = session(player)
        if (!players.remove(player)) return
        pendingActivations.remove(session.token)
        indexes.release(player.index)
        occupiedPlayerIds.remove(player.id)
        npcs.remove(MapInstance.privateTo(player.id))
        session.attachment.remove()
    }

    internal fun rejectPendingLogins() {
        reservations.values.forEach(::release)
        reservations.clear()
        pendingLogins.values.forEach { pending ->
            release(pending.reservation)
            pending.attachment.reject()
        }
        pendingLogins.clear()
    }

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
