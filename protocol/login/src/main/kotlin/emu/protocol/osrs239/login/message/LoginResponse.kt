package emu.protocol.osrs239.login.message

import emu.transport.message.OutgoingMessage

/** One unsigned client response code written while processing a login attempt. */
data class LoginResponse(val code: Int) : OutgoingMessage {
    init {
        require(code in 0..0xFF) { "login response code must fit an unsigned byte" }
    }

    companion object {
        const val SUCCESS = 2
        const val INVALID_CREDENTIALS = 3
        const val ACCOUNT_ONLINE = 5
        const val WORLD_FULL = 7
        const val LOGIN_SERVER_OFFLINE = 8
    }
}
