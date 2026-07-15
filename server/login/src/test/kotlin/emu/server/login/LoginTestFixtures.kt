package emu.server.login

import emu.crypto.Xtea
import emu.server.session.AccountId
import emu.server.session.AccountPrivilege
import emu.server.session.AuthenticationDecision
import emu.server.session.AuthenticatedAccount

internal const val TEST_LOGIN_NAME = "Test_Player"

internal val TEST_ACCOUNT =
    AuthenticatedAccount(
        accountId = AccountId(1),
        privilege = AccountPrivilege.PLAYER,
    )

internal fun acceptTestLogin(
    @Suppress("UNUSED_PARAMETER") username: String,
    @Suppress("UNUSED_PARAMETER") password: CharArray,
): AuthenticationDecision = AuthenticationDecision.Authenticated(TEST_ACCOUNT)

internal fun encryptedUsernameTail(username: String, seeds: IntArray): ByteArray {
    val cString = username.toByteArray(Charsets.ISO_8859_1) + 0
    val padded = cString.copyOf((cString.size + 7) / 8 * 8)
    return Xtea.encrypt(padded, seeds)
}
