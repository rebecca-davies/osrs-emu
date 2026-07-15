package emu.server.session

/** Account identity established by login without loading mutable character state. */
data class AuthenticatedAccount(
    val accountId: AccountId,
    val privilege: AccountPrivilege,
)
