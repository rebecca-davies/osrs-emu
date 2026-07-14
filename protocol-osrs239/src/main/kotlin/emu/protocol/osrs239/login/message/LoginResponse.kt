package emu.protocol.osrs239.login.message

import emu.netcore.message.OutgoingMessage

/**
 * The single response byte the client reads after sending its login block (`uo2.af()`). Code 2 =
 * success (rev239-login-facts.md §6); sent by `LoginHandler`. Other explicitly handled codes are
 * 15/21/23/29/61/64/69 — anything else falls through to the client's error table.
 */
data class LoginResponse(val code: Int) : OutgoingMessage
