package emu.persistence.character

/** Persisted Jagex chat visibility modes independent of the client varp address space. */
data class PlayerChatFiltersRecord(
    val publicMode: Int = 0,
    val privateMode: Int = 0,
    val tradeMode: Int = 0,
) {
    init {
        require(publicMode in 0..3) { "public chat mode must be in 0..3" }
        require(privateMode in 0..2) { "private chat mode must be in 0..2" }
        require(tradeMode in 0..2) { "trade chat mode must be in 0..2" }
    }
}
