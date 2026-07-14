package emu.gateway.login

import emu.crypto.Xtea
import emu.persistence.AuthenticationResult
import emu.persistence.PlayerPosition
import emu.persistence.PlayerRecord

internal const val TEST_LOGIN_NAME = "Test_Player"

internal val TEST_PLAYER =
    PlayerRecord(
        id = 1,
        username = "test player",
        displayName = TEST_LOGIN_NAME,
        position = PlayerPosition(SPAWN_X, SPAWN_Y, SPAWN_PLANE),
        playTimeSeconds = 0,
    )

internal fun acceptTestLogin(
    @Suppress("UNUSED_PARAMETER") username: String,
    @Suppress("UNUSED_PARAMETER") password: CharArray,
): AuthenticationResult = AuthenticationResult.Authenticated(TEST_PLAYER, created = false)

internal fun encryptedUsernameTail(username: String, seeds: IntArray): ByteArray {
    val cString = username.toByteArray(Charsets.ISO_8859_1) + 0
    val padded = cString.copyOf((cString.size + 7) / 8 * 8)
    return Xtea.encrypt(padded, seeds)
}
