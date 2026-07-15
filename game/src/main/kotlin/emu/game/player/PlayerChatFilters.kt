package emu.game.player

/** Authoritative Jagex public, private, and trade chat visibility modes for one player. */
class PlayerChatFilters(
    publicMode: Int = 0,
    privateMode: Int = 0,
    tradeMode: Int = 0,
) {
    var publicMode: Int = publicMode
        private set
    var privateMode: Int = privateMode
        private set
    var tradeMode: Int = tradeMode
        private set

    init {
        validate(publicMode, privateMode, tradeMode)
    }

    fun update(publicMode: Int, privateMode: Int, tradeMode: Int) {
        validate(publicMode, privateMode, tradeMode)
        this.publicMode = publicMode
        this.privateMode = privateMode
        this.tradeMode = tradeMode
    }

    private fun validate(publicMode: Int, privateMode: Int, tradeMode: Int) {
        require(publicMode in 0..3) { "public chat mode must be in 0..3" }
        require(privateMode in 0..2) { "private chat mode must be in 0..2" }
        require(tradeMode in 0..2) { "trade chat mode must be in 0..2" }
    }
}
