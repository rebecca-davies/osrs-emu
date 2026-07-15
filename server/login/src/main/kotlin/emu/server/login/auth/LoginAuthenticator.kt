package emu.server.login.auth

import emu.server.session.AuthenticationDecision

/** Authentication policy invoked after the login wire block has been validated. */
fun interface LoginAuthenticator {
    fun authenticate(username: String, password: CharArray): AuthenticationDecision
}
