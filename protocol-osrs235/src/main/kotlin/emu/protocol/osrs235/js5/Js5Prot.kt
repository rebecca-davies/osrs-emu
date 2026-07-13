package emu.protocol.osrs235.js5

import emu.netcore.prot.Prot

// JS5 opcode/size table for OSRS rev 235.
// See docs/superpowers/research/2026-07-13-rev235-protocol-facts.md §5.
object Js5Prot {
    val HANDSHAKE = Prot(15, 20)
    val GROUP_REQUEST = Prot(1, 3)            // urgent; 3-byte payload [archive u8][group u16]
    val GROUP_REQUEST_PREFETCH = Prot(0, 3)   // prefetch
    val GROUP_RESPONSE = Prot(-1, Prot.VAR_BYTE)  // opcode -1: registry key only, not on wire

    // JS5 control frames the client interleaves with group requests after the handshake. Each
    // carries a fixed 3-byte payload (see Js5Control/Js5ControlDecoder) and expects no response;
    // the gateway must still consume them or the pipeline drops the socket on the first one
    // (error_game_js5io).
    const val CONTROL_LOGGED_IN = 2
    const val CONTROL_LOGGED_OUT = 3
    const val CONTROL_XOR_KEY = 4       // b0 = the XOR key to apply to every subsequent response byte
    const val CONTROL_INIT = 6
    const val CONTROL_KEEPALIVE = 7

    // All control opcodes the gateway binds a decoder for; see gateway Main.kt.
    val CONTROL_OPCODES = intArrayOf(CONTROL_LOGGED_IN, CONTROL_LOGGED_OUT, CONTROL_XOR_KEY, CONTROL_INIT, CONTROL_KEEPALIVE)
}
