package emu.server.game.world.player

import emu.game.player.Player
import emu.server.game.network.connection.GameSession
import emu.server.game.persistence.PlayerWriteBack
import emu.server.session.handoff.GameSessionToken

/** Slot-indexed player membership with connection and write-back sidecars kept private. */
internal class PlayerList(maxPlayerIndex: Int) {
    private data class Slot(
        val player: Player,
        val session: GameSession,
        val writeBack: PlayerWriteBack,
    )

    private val slots = arrayOfNulls<Slot>(maxPlayerIndex + 1)
    private val ordered = ArrayList<Player>(maxPlayerIndex)
    private val byId = HashMap<Long, Player>(maxPlayerIndex)
    private val byToken = HashMap<GameSessionToken, Player>(maxPlayerIndex)

    val isEmpty: Boolean
        get() = ordered.isEmpty()

    fun add(
        player: Player,
        session: GameSession,
        writeBack: PlayerWriteBack,
    ) {
        check(slots[player.index] == null) { "player index ${player.index} is already occupied" }
        check(!byId.containsKey(player.id)) { "player id ${player.id} is already online" }
        check(!byToken.containsKey(session.token)) { "game session token is already attached" }
        slots[player.index] = Slot(player, session, writeBack)
        byId[player.id] = player
        byToken[session.token] = player
        ordered.add(player)
    }

    fun player(token: GameSessionToken): Player? = byToken[token]

    fun player(id: Long): Player? = byId[id]

    fun contains(playerId: Long): Boolean = byId.containsKey(playerId)

    fun contains(player: Player): Boolean = slots.getOrNull(player.index)?.player === player

    fun session(player: Player): GameSession = slot(player).session

    fun writeBack(player: Player): PlayerWriteBack = slot(player).writeBack

    fun collectActive(destination: MutableCollection<Player>) {
        for (player in ordered) {
            if (player.active && !player.loggingOut) destination += player
        }
    }

    fun collectCycle(destination: MutableCollection<Player>) {
        for (player in ordered) {
            if (
                !writeBack(player).snapshotTaken &&
                    (player.active || player.logoutRequested || player.loggingOut)
            ) {
                destination += player
            }
        }
    }

    fun collectAll(destination: MutableCollection<Player>) {
        destination.addAll(ordered)
    }

    fun all(): List<Player> = ordered.toList()

    fun remove(player: Player): Boolean {
        val slot = slots.getOrNull(player.index) ?: return false
        if (slot.player !== player) return false
        slots[player.index] = null
        byId.remove(player.id, player)
        byToken.remove(slot.session.token, player)
        ordered.remove(player)
        return true
    }

    private fun slot(player: Player): Slot {
        val slot = slots.getOrNull(player.index)
        check(slot?.player === player) { "player ${player.id} is not attached at index ${player.index}" }
        return slot
    }
}
