package emu.server.session

/** Client-visible reason an authenticated session cannot enter the world. */
enum class AuthenticationRejection {
    ALREADY_ONLINE,
    WORLD_FULL,
    WORLD_UNAVAILABLE,
}
