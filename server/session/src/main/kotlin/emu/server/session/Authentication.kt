package emu.server.session

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

sealed interface AuthenticationDecision {
    data class Authenticated(val principal: AuthenticatedPrincipal) : AuthenticationDecision

    data object Rejected : AuthenticationDecision
}

sealed interface AuthenticationCompletion {
    data class Accepted(val playerIndex: Int) : AuthenticationCompletion {
        init {
            require(playerIndex > 0) { "player index must be positive" }
        }
    }

    data class Rejected(val reason: AuthenticationRejection) : AuthenticationCompletion
}

enum class AuthenticationRejection {
    ALREADY_ONLINE,
    WORLD_FULL,
}
