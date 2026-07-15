package emu.server.session

/** Result of preparing the authenticated account to enter a world. */
sealed interface AuthenticationCompletion {
    data class Accepted(val playerIndex: Int) : AuthenticationCompletion {
        init {
            require(playerIndex > 0) { "player index must be positive" }
        }
    }

    data class Rejected(val reason: AuthenticationRejection) : AuthenticationCompletion
}
