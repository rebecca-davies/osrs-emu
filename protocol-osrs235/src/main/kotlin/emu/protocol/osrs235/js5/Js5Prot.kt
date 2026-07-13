package emu.protocol.osrs235.js5

import emu.netcore.prot.Prot

// JS5 opcode/size table for OSRS rev 235.
// See docs/superpowers/research/2026-07-13-rev235-protocol-facts.md §5.
object Js5Prot {
    val HANDSHAKE = Prot(15, 20)
    val GROUP_REQUEST = Prot(1, 3)            // urgent; 3-byte payload [archive u8][group u16]
    val GROUP_REQUEST_PREFETCH = Prot(0, 3)   // prefetch
    val GROUP_RESPONSE = Prot(-1, Prot.VAR_BYTE)  // opcode -1: registry key only, not on wire
}
