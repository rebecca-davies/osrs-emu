package emu.server.game.player

import emu.game.pathfinding.PlayerMovement
import emu.game.ui.ButtonAction
import emu.game.ui.ButtonActionRegistry
import emu.game.ui.buttonActions
import emu.game.varp.PlayerVarps
import emu.game.varp.VarpCatalog
import emu.game.varp.VarbitType
import emu.game.varp.VarpScope
import emu.game.varp.VarpTransmit
import emu.game.varp.VarpType

/** Rev-239 account configuration types currently owned by gameplay. */
internal object PlayerVarpTypes {
    /** 0=walk, 1=run (2=crawl is reserved by the client). */
    val RUN_MODE = VarpType(173, scope = VarpScope.PERMANENT)

    /** Base varp for the client's `has_displayname_transmitter` varbit. */
    private val DISPLAY_NAME_STATE = VarpType(1737, transmit = VarpTransmit.ON_CHANGE)
    val HAS_DISPLAY_NAME = VarbitType(8119, DISPLAY_NAME_STATE, 31..31)

    /** Server-owned account settings; dedicated chat packets publish them, never VARP packets. */
    val PUBLIC_CHAT_FILTER = VarpType(65533, scope = VarpScope.PERMANENT, transmit = VarpTransmit.NEVER)
    val PRIVATE_CHAT_FILTER = VarpType(65534, scope = VarpScope.PERMANENT, transmit = VarpTransmit.NEVER)
    val TRADE_CHAT_FILTER = VarpType(65535, scope = VarpScope.PERMANENT, transmit = VarpTransmit.NEVER)

    val CATALOG =
        VarpCatalog(
            RUN_MODE,
            DISPLAY_NAME_STATE,
            PUBLIC_CHAT_FILTER,
            PRIVATE_CHAT_FILTER,
            TRADE_CHAT_FILTER,
        )
}

/** Per-session lifecycle requests set only from game-cycle button actions. */
internal class PlayerSessionControl {
    var logoutRequested: Boolean = false
        private set

    fun requestLogout() {
        logoutRequested = true
    }
}

/**
 * Content registration for the currently-open rev-239 gameframe.
 *
 * Interface IDs remain here at the client/content edge. Both run controls share one action, and
 * operations are checked by the content handler just like an rsmod `onIfOverlayButton` script.
 */
internal fun playerButtonActions(
    movement: PlayerMovement,
    varps: PlayerVarps,
    control: PlayerSessionControl,
): ButtonActionRegistry = buttonActions {
    val toggleRun = ButtonAction { click ->
        if (click.op != 1) return@ButtonAction
        val enabled = varps[PlayerVarpTypes.RUN_MODE] != 1
        varps[PlayerVarpTypes.RUN_MODE] = if (enabled) 1 else 0
        movement.runEnabled = enabled
    }
    onButton(interfaceId = 116, componentId = 30, toggleRun)
    onButton(interfaceId = 160, componentId = 28, toggleRun)
    onButton(interfaceId = 182, componentId = 8) { click ->
        if (click.op == 1) control.requestLogout()
    }
}
