package emu.protocol.osrs239.game.prot

import emu.transport.prot.Prot

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

    /** Closes the subinterface tree attached to one component (opcode 23, fixed 4). */
    val IF_CLOSE_SUB = Prot(23, 4)

    /** Resynchronizes the complete interface tree (opcode 25, variable short). */
    val IF_RESYNC = Prot(25, Prot.VAR_SHORT)

    /** Hides or shows an interface component (opcode 63, fixed 5). */
    val IF_SET_HIDE = Prot(63, 5)

    /** Replaces one interface component's text (opcode 80, variable short). */
    val IF_SET_TEXT = Prot(80, Prot.VAR_SHORT)

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

    /** Var-short scene rebuild; login prepends GPI initialization to the normal rebuild body. */
    val REBUILD_NORMAL = Prot(49, Prot.VAR_SHORT)

    /** Signed-u16 world index and u8 active plane for subsequent cycle updates. */
    val SET_ACTIVE_WORLD = Prot(47, 3)

    /** Two-u8 scene-local origin for the following NPC-info coordinates. */
    val SET_NPC_UPDATE_ORIGIN = Prot(116, 2)

    /** Per-tick GPI movement and extended information, encoded as a var-short body. */
    val PLAYER_INFO = Prot(28, Prot.VAR_SHORT)

    /** Empty end-of-cycle marker. */
    val SERVER_TICK_END = Prot(83, 0)
}
