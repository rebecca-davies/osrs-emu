package emu.server.world.player

import emu.game.varp.VarpCatalog
import emu.game.varp.VarbitType
import emu.game.varp.VarpScope
import emu.game.varp.VarpTransmit
import emu.game.varp.VarpType

/** Rev-239 account configuration types owned by gameplay. */
internal object PlayerVarpCatalog {
    /** 0=walk, 1=run (2=crawl is reserved by the client). */
    val RUN_MODE = VarpType(173, scope = VarpScope.PERMANENT)

    /** Base varp for the client's `has_displayname_transmitter` varbit. */
    private val DISPLAY_NAME_STATE = VarpType(1737, transmit = VarpTransmit.ON_CHANGE)
    val HAS_DISPLAY_NAME = VarbitType(8119, DISPLAY_NAME_STATE, 31..31)

    /** Server-owned account settings; dedicated chat packets publish them, never VARP packets. */
    val PUBLIC_CHAT_FILTER = VarpType(65533, scope = VarpScope.PERMANENT, transmit = VarpTransmit.NEVER)
    val PRIVATE_CHAT_FILTER = VarpType(65534, scope = VarpScope.PERMANENT, transmit = VarpTransmit.NEVER)
    val TRADE_CHAT_FILTER = VarpType(65535, scope = VarpScope.PERMANENT, transmit = VarpTransmit.NEVER)

    val ALL =
        VarpCatalog(
            RUN_MODE,
            DISPLAY_NAME_STATE,
            PUBLIC_CHAT_FILTER,
            PRIVATE_CHAT_FILTER,
            TRADE_CHAT_FILTER,
        )
}
