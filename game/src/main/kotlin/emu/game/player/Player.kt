package emu.game.player

import emu.game.content.player.PlayerVarpCatalog
import emu.game.content.ui.gameframe.Gameframe
import emu.game.map.PlayerBuildArea
import emu.game.map.Tile
import emu.game.pathfinding.movement.PlayerMovement
import emu.game.queue.PlayerActionQueue
import emu.game.script.execution.PlayerScriptExecution
import emu.game.script.execution.PlayerScriptRequest
import emu.game.timer.PlayerTimers
import emu.game.ui.PlayerInterfaces
import emu.game.varp.PlayerVarps

/** World-thread-owned gameplay state used directly by Kotlin content scripts. */
open class Player(
    initialPosition: Tile,
    savedVarps: Map<Int, Int> = emptyMap(),
    initialChatFilters: PlayerChatFilters = PlayerChatFilters(),
) {
    val movement = PlayerMovement(initialPosition)
    val buildArea = PlayerBuildArea(initialPosition)
    val varps =
        PlayerVarps(PlayerVarpCatalog.ALL, savedVarps).apply {
            this[PlayerVarpCatalog.HAS_DISPLAY_NAME] = 1
        }
    val actionQueue = PlayerActionQueue<PlayerScriptRequest>()
    val timers = PlayerTimers()
    val interfaces = PlayerInterfaces()
    val chatFilters = initialChatFilters

    init {
        movement.runEnabled = varps[PlayerVarpCatalog.RUN_MODE] == 1
    }

    internal var activeScript: PlayerScriptExecution? = null
    var isAccessProtected: Boolean = false
        internal set
    internal var shutdownAccess: Boolean = false
        private set

    var active: Boolean = false
        private set
    var logoutRequested: Boolean = false
        private set
    var loggingOut: Boolean = false
        private set
    /** Whether protected content may start for this player. */
    fun canAccess(): Boolean = shutdownAccess || activeScript == null && !isAccessProtected

    /** Clears weak work, closes protected interfaces, and reports whether close scripts were queued. */
    fun closeModal(): Boolean {
        actionQueue.clearWeak()
        return interfaces.closeModal()
    }

    /** Enables 2004Scape's unconditional shutdown access and abandons suspended content. */
    fun enableShutdownAccess() {
        shutdownAccess = true
        discardActiveScript()
    }

    internal fun discardActiveScript() {
        activeScript?.discard()
        activeScript = null
        isAccessProtected = false
    }

    /** Synchronizes the base gameframe, runs LOGIN content, then enables ordinary play. */
    open fun activate(
        gameframe: Gameframe,
        beforeActivation: () -> Unit = {},
    ) {
        if (logoutRequested || loggingOut) return
        gameframe.open(interfaces)
        interfaces.markClientSynchronized()
        varps.markClientSynchronized()
        beforeActivation()
        if (logoutRequested || loggingOut) return
        active = true
    }

    /** Requests removal through the world's normal write-back lifecycle. */
    fun requestLogout() {
        if (!loggingOut) logoutRequested = true
    }

    /** Consumes a pending request at the LOGOUT phase boundary. */
    fun beginLogout(): Boolean {
        if (!logoutRequested || loggingOut) return false
        logoutRequested = false
        loggingOut = true
        return true
    }
}
