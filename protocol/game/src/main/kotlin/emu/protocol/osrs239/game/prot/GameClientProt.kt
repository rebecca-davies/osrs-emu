package emu.protocol.osrs239.game.prot

import emu.transport.prot.Prot

/**
 * Complete rev-239 client-to-server opcode/size table recovered from the injected client's
 * `ClientProt` (`jf`) static initializer.
 *
 * A complete table matters even before every packet has a decoder: the gateway must consume each
 * body at its declared boundary so one ISAAC value is used per opcode, never per payload byte.
 */
object GameClientProt {
    private val sizes =
        intArrayOf(
            2, 10, 1, 3, 0, 0, -1, 8, 4, -1, 5, 7, -1, 4, 3, 8,
            2, -1, 3, 4, -1, 3, 3, 11, 0, 7, -1, 16, -1, 1, 7, 8,
            2, 4, -1, 7, 7, 3, -1, 4, 10, 3, 7, 8, 1, 4, 8, 9,
            16, 3, 9, 4, 15, 3, -2, -1, -2, 3, 6, 3, -1, 2, 15, 8,
            -1, -1, 6, -1, 7, -1, 3, 3, 11, 8, 8, 4, 15, 26, 2, 11,
            3, 7, -1, 3, -1, 4, 11, 11, 7, 0, 11, 22, 3, 3, 7, 0,
            -1, -1, 2, 4, 3, -1, 4, 8, 8, 1, 3, 7, -2, 7, -1, -2,
            1, 15, -1, 6, 8,
        )
    private val protocols = Array(sizes.size) { opcode -> Prot(opcode, sizes[opcode]) }

    /** Number of valid client opcodes in this revision. */
    val count: Int
        get() = protocols.size

    /** Ground-click movement request: variable-byte frame with a five-byte decoded body. */
    val MOVE_GAMECLICK: Prot = protocols[114]

    /** Generic interface button operation with packed component, slot, object, and operation. */
    val IF_BUTTONX: Prot = protocols[47]

    /** Developer-console input, sent without the leading `::` marker. */
    val CLIENT_CHEAT: Prot = protocols[34]

    /** Public/private/trade visibility triplet selected from the chatbox controls. */
    val SET_CHAT_FILTER_SETTINGS: Prot = protocols[59]

    /** Public or clan-channel text with colour/effect metadata and Huffman payload. */
    val MESSAGE_PUBLIC: Prot = protocols[69]

    /** Applet focus transition reported as a one-byte boolean. */
    val EVENT_APPLET_FOCUS: Prot = protocols[29]

    /** Periodic zero-length packet that keeps an otherwise idle game connection alive. */
    val NO_TIMEOUT: Prot = protocols[89]

    /** Returns the declared protocol entry, or null for an opcode outside rev 239's table. */
    fun find(opcode: Int): Prot? = protocols.getOrNull(opcode)

    init {
        require(MOVE_GAMECLICK.size == Prot.VAR_BYTE)
        require(IF_BUTTONX.size == 9)
        require(CLIENT_CHEAT.size == Prot.VAR_BYTE)
        require(SET_CHAT_FILTER_SETTINGS.size == 3)
        require(MESSAGE_PUBLIC.size == Prot.VAR_BYTE)
        require(EVENT_APPLET_FOCUS.size == 1)
        require(NO_TIMEOUT.size == 0)
    }
}
