package emu.game.chat

/** Input admitted from the network and consumed only by the authoritative game cycle. */
sealed interface ChatInput

/** A validated public-chat request ready for audit admission and local publication. */
data class PublicChatInput(
    val colour: Int,
    val effect: Int,
    val text: String,
    val pattern: ByteArray?,
) : ChatInput {
    init {
        require(text.isNotBlank() && text.length <= MAX_CHAT_LENGTH) { "invalid public chat length" }
        require(colour in 0..20) { "invalid public chat colour" }
        require(effect in 0..5) { "invalid public chat effect" }
        require(pattern == null || pattern.size in 1..8) { "invalid public chat pattern" }
    }

    override fun equals(other: Any?): Boolean =
        other is PublicChatInput && colour == other.colour && effect == other.effect &&
            text == other.text && pattern.contentEquals(other.pattern)

    override fun hashCode(): Int = listOf(colour, effect, text, pattern?.contentHashCode()).hashCode()

    companion object {
        const val MAX_CHAT_LENGTH = 100
    }
}

/** Complete client chat visibility selection; the three modes are validated on the game cycle. */
data class ChatFilterInput(
    val publicFilter: Int,
    val privateFilter: Int,
    val tradeFilter: Int,
) : ChatInput
