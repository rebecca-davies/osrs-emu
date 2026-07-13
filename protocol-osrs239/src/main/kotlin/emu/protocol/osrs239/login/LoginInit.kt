package emu.protocol.osrs239.login

import emu.netcore.message.IncomingMessage

// Opcode 14: the client sends just the single opcode byte, no payload (LoginProt.INIT size = 0).
// See docs/superpowers/research/2026-07-14-rev239-login-facts.md §1.
object LoginInit : IncomingMessage
