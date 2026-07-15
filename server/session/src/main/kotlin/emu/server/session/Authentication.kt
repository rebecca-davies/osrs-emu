package emu.server.session

/** Privilege established during account authentication. */
enum class AccountPrivilege {
    PLAYER,
    MODERATOR,
    ADMINISTRATOR,
}

/** Account identity established by login without loading mutable character state. */
data class AuthenticatedPrincipal(
    val accountId: Long,
    val username: String,
    val displayName: String,
    val privilege: AccountPrivilege,
) {
    init {
        require(accountId > 0) { "account id must be positive" }
        require(username.isNotBlank()) { "username must not be blank" }
        require(displayName.isNotBlank()) { "display name must not be blank" }
    }
}

/** Result of validating account credentials. */
sealed interface AuthenticationDecision {
    data class Authenticated(val principal: AuthenticatedPrincipal) : AuthenticationDecision

    data object Rejected : AuthenticationDecision
}

/** Result of reserving the authenticated account in a world. */
sealed interface AuthenticationCompletion {
    data class Accepted(val playerIndex: Int) : AuthenticationCompletion {
        init {
            require(playerIndex > 0) { "player index must be positive" }
        }
    }

    data class Rejected(val reason: AuthenticationRejection) : AuthenticationCompletion
}

/** Client-visible reason an authenticated session cannot enter the world. */
enum class AuthenticationRejection {
    ALREADY_ONLINE,
    WORLD_FULL,
}
