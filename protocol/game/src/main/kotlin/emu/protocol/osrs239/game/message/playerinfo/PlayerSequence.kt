package emu.protocol.osrs239.game.message.playerinfo

/** Rev-239 player sequence id and client-cycle delay. */
data class PlayerSequence(
    val id: Int,
    val delay: Int = 0,
) {
    init {
        require(id in -1..MAX_SEQUENCE_ID) { "sequence id must be in -1..$MAX_SEQUENCE_ID" }
        require(delay in UBYTE_RANGE) { "sequence delay must fit an unsigned byte" }
    }

    private companion object {
        const val MAX_SEQUENCE_ID = 0xFFFE
        val UBYTE_RANGE = 0..0xFF
    }
}
