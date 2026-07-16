package emu.server.game.world.player

import emu.game.map.Tile
import emu.game.player.Player
import emu.game.player.PlayerChatFilters
import emu.persistence.character.model.CharacterRecord
import emu.server.session.account.AccountPrivilege

/** Authoritative gameplay identity and state for one player in this world. */
internal class WorldPlayer(record: CharacterRecord, val privilege: AccountPrivilege) :
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
}
