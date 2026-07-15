package emu.protocol.osrs239.game.message

/** Huffman-ready public-chat extended info carried inside the local player's PLAYER_INFO update. */
data class PlayerPublicChat(
    val colour: Int,
    val effect: Int,
    val modIcon: Int,
    val encodedText: ByteArray,
    val pattern: ByteArray? = null,
    val autotyper: Boolean = false,
) {
    init {
        require(colour in 0..20 && effect in 0..5) { "invalid public-chat style" }
        require(modIcon in 0..255) { "invalid public-chat mod icon" }
        require(encodedText.isNotEmpty() && encodedText.size <= 255) { "invalid Huffman chat payload length" }
        require(pattern == null || pattern.size in 1..8) { "invalid public-chat pattern" }
    }
}
