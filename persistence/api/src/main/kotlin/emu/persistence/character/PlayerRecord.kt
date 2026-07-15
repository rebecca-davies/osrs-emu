package emu.persistence.character

import emu.persistence.account.PlayerRank

/** Character state loaded for one live world session. */
data class PlayerRecord(
    val id: Long,
    val username: String,
    val displayName: String,
    val position: PlayerPosition,
    val playTimeSeconds: Long,
    val rank: PlayerRank = PlayerRank.PLAYER,
    val varps: Map<Int, Int> = emptyMap(),
) {
    override fun toString(): String =
        "PlayerRecord(id=$id, position=$position, playTimeSeconds=$playTimeSeconds, rank=$rank, varps=${varps.size})"
}
