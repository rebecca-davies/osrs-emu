package emu.persistence.account

/** Stored authentication material. Deliberately has no generated string representation. */
class StoredAccount(
    val account: AccountRecord,
    val passwordHash: String,
)
