package emu.protocol.osrs239.game.prot

import emu.netcore.prot.Prot

/**
 * Game (post-login) server->client opcode/size table for OSRS rev 239 — the implemented subset of
 * the client's 149-entry `ServerProt` table (`jc.java`).
 */
object GameServerProt {
    /** Private-chat visibility mode (opcode 5, fixed 1). */
    val CHAT_FILTER_SETTINGS_PRIVATE = Prot(5, 1)
    /** Clean player logout (opcode 57, empty). */
    val LOGOUT = Prot(57, 0)

    /** Account/site settings CP-1252 string (opcode 67, variable byte). */
    val SITE_SETTINGS = Prot(67, Prot.VAR_BYTE)

    /** Initial public/trade chat filters (opcode 124, fixed 2). */
    val CHAT_FILTER_SETTINGS = Prot(124, 2)

    /** Small signed-byte varp update (opcode 97, fixed 3). */
    val VARP_SMALL = Prot(97, 3)

    /** Full 32-bit varp update (opcode 12, fixed 6). */
    val VARP_LARGE = Prot(12, 6)

    /** Declares the byte length of the following atomic packet group (opcode 93, fixed 2). */
    val PACKET_GROUP_START = Prot(93, 2)

    /**
     * A chatbox game message (opcode 74, variable byte): a smart-1-or-2 `chattype`, a name-present
     * flag, an optional NUL-terminated sender name, then the NUL-terminated message text.
     */
    val MESSAGE_GAME = Prot(74, Prot.VAR_BYTE)

    /** Root/dynamic world-entity update (opcode 122, variable short). */
    val WORLD_ENTITY_INFO = Prot(122, Prot.VAR_SHORT)

    /** Small-coordinate NPC update stream (opcode 85, variable short). */
    val NPC_INFO = Prot(85, Prot.VAR_SHORT)

    /** Clears/selects an 8x8 scene zone (opcode 54, fixed 3). */
    val UPDATE_ZONE_FULL_FOLLOWS = Prot(54, 3)

    /** Replaces an inventory container (opcode 22, variable short). */
    val UPDATE_INV_FULL = Prot(22, Prot.VAR_SHORT)

    /** Opens the top-level interface (opcode 96, fixed 2). */
    val IF_OPEN_TOP = Prot(96, 2)

    /** Attaches a subinterface to a component (opcode 7, fixed 7). */
    val IF_OPEN_SUB = Prot(7, 7)

    /** Resynchronizes the complete interface tree (opcode 25, variable short). */
    val IF_RESYNC = Prot(25, Prot.VAR_SHORT)

    /** Hides or shows an interface component (opcode 63, fixed 5). */
    val IF_SET_HIDE = Prot(63, 5)

    /** Invokes a cache client script (opcode 114, variable short). */
    val RUN_CLIENT_SCRIPT = Prot(114, Prot.VAR_SHORT)

    /** Replaces one skill's levels/experience (opcode 46, fixed 7). */
    val UPDATE_STAT = Prot(46, 7)

    /** Sets normal/disabled minimap state (opcode 43, fixed 1). */
    val MINIMAP_TOGGLE = Prot(43, 1)

    /** Stops area ambience (opcode 138, fixed 1, two-byte smart opcode on the wire). */
    val AMBIENCE_STOP = Prot(138, 1)

    /** Selects the camera's entity or coordinate target (opcode 87, fixed 5). */
    val CAM_TARGET_V4 = Prot(87, 5)

    /** NPC option visibility (opcode 75, fixed 1). */
    val HIDE_NPC_OPS = Prot(75, 1)

    /** Location option visibility (opcode 21, fixed 1). */
    val HIDE_LOC_OPS = Prot(21, 1)

    /** Ground-object option visibility (opcode 73, fixed 1). */
    val HIDE_OBJ_OPS = Prot(73, 1)

    /** Clears all client varps (opcode 44, empty). */
    val VARP_RESET = Prot(44, 0)

    /** Restores normal camera mode (opcode 3, empty). */
    val CAM_RESET = Prot(3, 0)

    /** Clears entity animations (opcode 92, empty). */
    val RESET_ANIMS = Prot(92, 0)

    /** Run energy in hundredths of a percent (opcode 64, fixed 2). */
    val UPDATE_RUN_ENERGY = Prot(64, 2)

    /** Carried run weight (opcode 31, fixed 2). */
    val UPDATE_RUN_WEIGHT = Prot(31, 2)

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
