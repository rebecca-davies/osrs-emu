package emu.protocol.osrs239.game.prot

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
     * Sets the client's **active world** context for the packets that follow (player info, npc
     * info, zone updates). Opcode 47 (rsprot `SET_ACTIVE_WORLD_V2`), fixed size 3: a signed u16
     * world index (`0` = the root/top-level world since rev 237) followed by a u8 active level
     * (the plane). rsmod (`RspCycle.flush`) and the rsprot guide both send this as the **first**
     * packet of every post-tick flush, before PLAYER_INFO — the rev-235+ world-entity system
     * processes player/npc info relative to the active world, so without it being set to the root
     * world the client has no valid world context and drops the connection right after login.
     */
    val SET_ACTIVE_WORLD = Prot(47, 3)

    /**
     * Sets the base coordinate the following NPC-info bit stream is encoded relative to. Opcode 116
     * (rsprot `SET_NPC_UPDATE_ORIGIN`), fixed size 2: `[originX, originZ]` (each u8), where the
     * origin is the local player's tile minus the 13x13 build-area base
     * (`baseX = (zoneX - 6) * 8`). rsmod (`RspCycle.flush`) sends this **every** cycle, right after
     * PLAYER_INFO and before NPC_INFO — the rev-235+ npc-info protocol reads coordinates relative
     * to this origin, so a cycle that never sets it leaves the client's npc-info state uninitialized.
     */
    val SET_NPC_UPDATE_ORIGIN = Prot(116, 2)

    /**
     * Per-tick player info: local + other players' movement bits, and extended info (appearance,
     * chat, animation, ...). Opcode 28 (`jc.bv`), size -2 (var-short). See [PlayerInfoEncoder] for
     * the current minimal (single-local-player, appearance-only) encoding and its confidence
     * breakdown (§4b/§4c — MEDIUM/LOW on the exact bit layout, iterate against the real client).
     */
    val PLAYER_INFO = Prot(28, Prot.VAR_SHORT)

    /**
     * End-of-tick marker: an empty, fixed-size packet sent after each tick's PLAYER_INFO so the
     * client finalizes the cycle. Opcode 83 (rsprot `GameServerProtId.SERVER_TICK_END`), size 0.
     * rsmod writes it once per player at the tail of every post-tick flush; without it the client
     * receives PLAYER_INFO but never gets the per-cycle terminator and drops the connection.
     */
    val SERVER_TICK_END = Prot(83, 0)
}
