package emu.protocol.osrs239.js5

import emu.netcore.prot.Prot

/**
 * JS5 opcode/size table for OSRS rev 235.
 * See docs/superpowers/research/2026-07-13-rev235-protocol-facts.md §5.
 */
object Js5Prot {
    val HANDSHAKE = Prot(15, 20)

    /** Urgent group request: 3-byte payload `[archive u8][group u16]`. */
    val GROUP_REQUEST = Prot(1, 3)

    /** Same wire shape as [GROUP_REQUEST], but for prefetch (low-priority) requests. */
    val GROUP_REQUEST_PREFETCH = Prot(0, 3)

    /** Opcode -1: registry key only for [Js5ResponseEncoder] — not a real opcode on the wire. */
    val GROUP_RESPONSE = Prot(-1, Prot.VAR_BYTE)

    /**
     * JS5 control frame opcodes the client interleaves with group requests after the handshake.
     * Each carries a fixed 3-byte payload (see `Js5Control`/`Js5ControlDecoder`) and expects no
     * response; the gateway must still consume them or the pipeline drops the socket on the first
     * one (client-reported `error_game_js5io`).
     */
    const val CONTROL_LOGGED_IN = 2
    const val CONTROL_LOGGED_OUT = 3

    /** Payload byte 0 is the XOR key to apply to every subsequent response byte. */
    const val CONTROL_XOR_KEY = 4
    const val CONTROL_INIT = 6
    const val CONTROL_KEEPALIVE = 7

    /** All control opcodes the gateway binds a decoder for; see gateway `Main.kt`. */
    val CONTROL_OPCODES = intArrayOf(CONTROL_LOGGED_IN, CONTROL_LOGGED_OUT, CONTROL_XOR_KEY, CONTROL_INIT, CONTROL_KEEPALIVE)
}
