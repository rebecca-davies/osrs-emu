package emu.game.ui

/** Cache client-script identifier resolved from revision-pinned content. */
@JvmInline
value class ClientScript(val id: Int) {
    init {
        require(id >= 0) { "client script id must be non-negative" }
    }
}
