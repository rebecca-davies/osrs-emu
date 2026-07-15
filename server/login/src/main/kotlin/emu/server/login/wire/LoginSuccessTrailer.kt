package emu.server.login.wire

import emu.server.session.AccountPrivilege

private const val LOGIN_INFO_SPAN = 37
private const val LOGIN_INFO_PAYLOAD_LENGTH = 34
private const val PLAYER_INDEX_OFFSET = 7
internal const val LOGIN_SUCCESS_TRAILER_LENGTH = 35
internal const val LOGIN_RIGHTS_TRAILER_OFFSET = 6
internal const val LOGIN_PLAYER_MOD_TRAILER_OFFSET = 7

/** Builds the fresh-login account block after the world reserves a player index. */
internal fun loginSuccessTrailer(
    privilege: AccountPrivilege,
    playerIndex: Int,
): ByteArray {
    require(playerIndex in 1 until 2_048) { "player index out of range: $playerIndex" }
    val block = ByteArray(LOGIN_INFO_PAYLOAD_LENGTH)
    block[LOGIN_RIGHTS_TRAILER_OFFSET - 1] = privilege.staffModLevel.toByte()
    block[LOGIN_PLAYER_MOD_TRAILER_OFFSET - 1] = if (privilege.playerModerator) 1 else 0
    block[PLAYER_INDEX_OFFSET] = (playerIndex ushr 8).toByte()
    block[PLAYER_INDEX_OFFSET + 1] = playerIndex.toByte()
    return byteArrayOf(LOGIN_INFO_SPAN.toByte()) + block
}

private val AccountPrivilege.staffModLevel: Int
    get() = ordinal

private val AccountPrivilege.playerModerator: Boolean
    get() = this != AccountPrivilege.PLAYER
