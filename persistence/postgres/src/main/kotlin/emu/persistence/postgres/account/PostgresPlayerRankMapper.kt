package emu.persistence.postgres.account

import emu.persistence.account.PlayerRank

/** Maps stable database rank identifiers to persistence API values. */
internal object PostgresPlayerRankMapper {
    fun fromId(id: Int): PlayerRank =
        PlayerRank.entries.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("unsupported player rank id $id")
}
