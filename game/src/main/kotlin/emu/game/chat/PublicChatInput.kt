package emu.game.chat

/** A validated public-chat action ready for audit queuing and local publication. */
class PublicChatInput(
    val colour: Int,
    val effect: Int,
    val text: String,
    pattern: ByteArray? = null,
) : ChatInput {
    private val patternBytes = pattern?.copyOf()

    val pattern: ByteArray?
        get() = patternBytes?.copyOf()

    init {
        require(text.isNotBlank() && text.length <= MAX_CHAT_LENGTH) { "invalid public chat length" }
        require(colour in 0..20) { "invalid public chat colour" }
        require(effect in 0..5) { "invalid public chat effect" }
        if (colour in PATTERN_COLOURS) {
            require(patternBytes?.size == colour - PATTERN_COLOUR_BASE) {
                "public chat colour $colour requires ${colour - PATTERN_COLOUR_BASE} pattern bytes"
            }
        } else {
            require(patternBytes == null) { "public chat colours 0..12 cannot carry a pattern" }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is PublicChatInput && colour == other.colour && effect == other.effect &&
            text == other.text && patternBytes.contentEquals(other.patternBytes)

    override fun hashCode(): Int = listOf(colour, effect, text, patternBytes?.contentHashCode()).hashCode()

    companion object {
        const val MAX_CHAT_LENGTH = 100
        private const val PATTERN_COLOUR_BASE = 12
        private val PATTERN_COLOURS = 13..20
    }
}
