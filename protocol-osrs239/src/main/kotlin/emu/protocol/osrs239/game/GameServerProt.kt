package emu.protocol.osrs239.game

import emu.netcore.prot.Prot

/**
 * Game (post-login) server->client opcode/size table for OSRS rev 239 — the subset of the
 * 149-entry `ServerProt` table (`jc.java`) that this milestone needs.
 * See docs/superpowers/research/2026-07-14-rev239-ingame-facts.md §1c.
 */
object GameServerProt {
    /**
     * Scene/map rebuild around the local player: GPI-init (bit-packed local + reference coords,
     * §4a) followed by the 13x13 base-zone coordinates (§3b). Opcode 49 (`jc.bw`), size -2
     * (var-short). Also the FIRST game packet sent after login — the login state machine there
     * reads GPI-init + this body positionally, back-to-back under one opcode+length (§3a); the
     * in-game (per-tick) op 49 sends only the rebuild body, no GPI-init prefix.
     */
    val REBUILD_NORMAL = Prot(49, Prot.VAR_SHORT)

    /**
     * Per-tick player info: local + other players' movement bits, and extended info (appearance,
     * chat, animation, ...). Opcode 28 (`jc.bv`), size -2 (var-short). Defined here for the codec
     * registry now; its encoder/handler is a later task (§4b).
     */
    val PLAYER_INFO = Prot(28, Prot.VAR_SHORT)
}
