package emu.server.world.entity

import emu.game.pathfinding.Tile
import emu.game.player.Player
import emu.game.player.PlayerChatFilters
import emu.persistence.account.PlayerRank
import emu.persistence.character.PlayerRecord

/** Authoritative gameplay identity and state for one player in this world. */
internal class WorldPlayer(record: PlayerRecord) :
    Player(
        Tile(record.position.x, record.position.y, record.position.plane),
        record.varps,
        PlayerChatFilters(
            record.chatFilters.publicMode,
            record.chatFilters.privateMode,
            record.chatFilters.tradeMode,
        ),
    ) {
    val id: Long = record.id
    val displayName: String = record.displayName
    val rank: PlayerRank = record.rank
}
