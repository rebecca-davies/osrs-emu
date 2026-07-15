package emu.persistence.account

/** Identity and privilege loaded during authentication. */
data class AccountRecord(
    val id: Long,
    val username: String,
    val displayName: String,
    val rank: PlayerRank,
) {
    override fun toString(): String = "AccountRecord(id=$id, rank=$rank)"
}
