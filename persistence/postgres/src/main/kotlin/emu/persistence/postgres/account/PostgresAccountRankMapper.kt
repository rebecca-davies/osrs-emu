package emu.persistence.postgres.account

import emu.persistence.account.AccountRank

/** Maps stable database rank identifiers to persistence API values. */
internal object PostgresAccountRankMapper {
    fun fromId(id: Int): AccountRank =
        AccountRank.entries.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("unsupported account rank id $id")
}
