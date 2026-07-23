package emu.game.player

import emu.game.chat.PublicChatInput
import emu.game.content.player.PlayerVarpCatalog
import emu.game.content.ui.gameframe.Gameframe
import emu.game.entity.EntityHealthBar
import emu.game.entity.EntityHitmark
import emu.game.entity.EntityInfoSnapshot
import emu.game.entity.EntityInfoUpdates
import emu.game.entity.EntitySpotAnimation
import emu.game.loc.Loc
import emu.game.map.MapInstance
import emu.game.map.PlayerBuildArea
import emu.game.map.Tile
import emu.game.pathfinding.movement.PlayerMovement
import emu.game.player.appearance.CharacterAppearance
import emu.game.player.inventory.PlayerInventory
import emu.game.player.inventory.PlayerWorn
import emu.game.player.interaction.PlayerInteraction
import emu.game.player.stat.PlayerStats
import emu.game.queue.PlayerActionQueue
import emu.game.script.execution.PlayerScriptExecution
import emu.game.script.execution.PlayerScriptRequest
import emu.game.timer.PlayerTimers
import emu.game.ui.PlayerInterfaces
import emu.game.varp.PlayerVarps

/** Authoritative world-thread-owned state for one live player character. */
class Player(
    val id: Long,
    val index: Int,
    val displayName: String,
    val staffModLevel: StaffModLevel,
    initialPosition: Tile,
    savedVarps: Map<Int, Int> = emptyMap(),
    initialChatFilters: PlayerChatFilters = PlayerChatFilters(),
    initialAppearance: CharacterAppearance = CharacterAppearance.DEFAULT,
) {
    private var pendingRoute: RouteRequest? = null
    private val gameMessages = ArrayDeque<String>(MAX_PENDING_GAME_MESSAGES)
    private val infoUpdates = EntityInfoUpdates()

    val movement = PlayerMovement(initialPosition)
    val buildArea = PlayerBuildArea(initialPosition)

    var mapInstance: MapInstance = MapInstance.SHARED
        private set
    var interaction: PlayerInteraction? = null
        private set
    val varps =
        PlayerVarps(PlayerVarpCatalog.ALL, savedVarps).apply {
            this[PlayerVarpCatalog.HAS_DISPLAY_NAME] = 1
        }
    val actionQueue = PlayerActionQueue<PlayerScriptRequest>()
    val timers = PlayerTimers()
    val interfaces = PlayerInterfaces()
    val chatFilters = initialChatFilters
    val stats = PlayerStats()
    val inventory = PlayerInventory()
    val worn = PlayerWorn()

    var appearance: CharacterAppearance = initialAppearance
        private set

    var publicChat: PublicChatInput? = null
        private set

    var animationUpdate: PlayerAnimation? = null
        private set

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
    var idleLogoutRequested: Boolean = false
        private set
    var loggingOut: Boolean = false
        private set
    private var modalCloseRequested: Boolean = false

    init {
        require(id > 0) { "player id must be positive" }
        require(index in PLAYER_INDEX_RANGE) { "player index must be in 1..2047" }
        require(displayName.isNotBlank()) { "player display name must not be blank" }
    }

    /** Whether ordinary queued work may run without violating protected or modal state. */
    fun canAccess(): Boolean = shutdownAccess || canAcquireProtectedAccess() && !interfaces.hasModal()

    /** Replaces the route request that the world map will resolve at its next phase boundary. */
    fun walkTo(destination: Tile, temporaryRun: Boolean? = null) {
        require(destination.plane == movement.position.plane) { "a walking route cannot change plane" }
        pendingRoute = RouteRequest.Coordinate(destination, temporaryRun)
    }

    /** Replaces the route request with one that stops at an operable tile around [target]. */
    fun pathTo(target: Loc, temporaryRun: Boolean? = null) {
        require(target.tile.plane == movement.position.plane) { "a walking route cannot change plane" }
        pendingRoute = RouteRequest.Location(target, temporaryRun)
    }

    /** Replaces the route request with one that stops beside a pathing entity's current footprint. */
    fun pathToEntity(
        position: Tile,
        size: Int,
        temporaryRun: Boolean? = null,
    ) {
        require(position.plane == movement.position.plane) {
            "a pathing-entity route cannot change plane"
        }
        require(size in 1..0xFF) { "pathing-entity size must fit an unsigned byte" }
        pendingRoute = RouteRequest.PathingEntity(position, size, temporaryRun)
    }

    /** Replaces the current pathing interaction. */
    fun beginInteraction(value: PlayerInteraction) {
        interaction = value
    }

    /** Completes the expected interaction without clearing a newer replacement. */
    fun completeInteraction(value: PlayerInteraction): Boolean {
        if (interaction !== value) return false
        clearInteraction()
        return true
    }

    /** Cancels any pathing interaction while leaving ordinary player state untouched. */
    fun clearInteraction() {
        interaction = null
    }

    /** Clears interaction, unresolved routing, weak work, and modal state without changing established waypoints. */
    fun clearPendingAction() {
        pendingRoute = null
        clearInteraction()
        closeModal()
    }

    /** Stops ordinary movement and discards an unresolved route request. */
    fun stopMoving() {
        pendingRoute = null
        movement.clearRoute()
    }

    /** Teleports to [destination], optionally entering another server-side map instance. */
    fun teleportTo(destination: Tile, instance: MapInstance = mapInstance) {
        pendingRoute = null
        clearInteraction()
        mapInstance = instance
        movement.teleportTo(destination)
    }

    /** Publishes at most one public-chat update during a cycle. */
    fun publishPublicChat(message: PublicChatInput): Boolean {
        if (publicChat != null) return false
        publicChat = message
        return true
    }

    /** Queues one bounded game message for the next client-output phase. */
    fun messageGame(text: String): Boolean {
        if (gameMessages.size >= MAX_PENDING_GAME_MESSAGES) return false
        gameMessages.addLast(text)
        return true
    }

    /** Changes the appearance published by the next player-info update. */
    fun changeAppearance(value: CharacterAppearance) {
        if (appearance == value) return
        appearance = value
    }

    /** Consumes queued game messages in insertion order. */
    fun takeGameMessages(): List<String> {
        if (gameMessages.isEmpty()) return emptyList()
        val messages = ArrayList<String>(gameMessages.size)
        while (gameMessages.isNotEmpty()) messages += gameMessages.removeFirst()
        return messages
    }

    /** Whether a script may take protected access, independently of the interface it handles. */
    internal fun canAcquireProtectedAccess(): Boolean =
        shutdownAccess || activeScript == null && !isAccessProtected

    /** Clears weak work and closes any protected interface or client-input dialog. */
    fun closeModal(): Boolean {
        actionQueue.clearWeak()
        val closedInterface = interfaces.closeModal()
        val discardedInput = discardActiveInputScript()
        return closedInterface || discardedInput
    }

    /** Requests an animation for this cycle; `-1` stops the current client animation. */
    fun playAnimation(id: Int, delay: Int = 0) {
        animationUpdate = PlayerAnimation(id, delay)
    }

    /** Shows one bounded hitmark in this cycle's player information update. */
    fun showHitmark(damage: Int, delay: Int = 0): Boolean =
        infoUpdates.showHitmark(EntityHitmark(damage, delay))

    /** Replaces this cycle's health-bar update with the latest authoritative value. */
    fun showHealthBar(current: Int, maximum: Int, delay: Int = 0) {
        infoUpdates.showHealthBar(EntityHealthBar(current, maximum, delay))
    }

    /** Sets one bounded spot-animation slot for this cycle's player information update. */
    fun playSpotAnimation(id: Int, delay: Int = 0, height: Int = 0, slot: Int = 0): Boolean =
        infoUpdates.playSpotAnimation(EntitySpotAnimation(id, delay, height, slot))

    /** Immutable information visuals retained for every observer during this cycle. */
    fun infoSnapshot(): EntityInfoSnapshot? = infoUpdates.snapshot()

    /** Clears per-cycle movement and player-info state after the global output phases. */
    fun finishCycle() {
        movement.finishCycle()
        infoUpdates.finishCycle()
        animationUpdate = null
        publicChat = null
    }

    /** Defers a client modal close until the player queue boundary. */
    fun requestModalClose() {
        modalCloseRequested = true
    }

    /** Consumes one coalesced client modal close request. */
    fun consumeModalCloseRequest(): Boolean {
        if (!modalCloseRequested) return false
        modalCloseRequested = false
        return true
    }

    /** Enables 2004Scape's unconditional shutdown access and abandons suspended content. */
    fun enableShutdownAccess() {
        shutdownAccess = true
        discardActiveScript()
    }

    /** Abandons suspended content and releases its protected player access. */
    fun discardActiveScript() {
        val execution = activeScript
        activeScript = null
        isAccessProtected = false
        execution?.discard()
    }

    /** Abandons a script paused for client input without affecting delayed scripts. */
    private fun discardActiveInputScript(): Boolean {
        if (activeScript?.isWaitingForInput() != true) return false
        discardActiveScript()
        return true
    }

    /** Synchronizes the base gameframe, runs LOGIN content, then enables ordinary play. */
    fun activate(
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

    /** Requests the idle-specific path through the normal logout lifecycle. */
    fun requestIdleLogout() {
        if (!loggingOut) idleLogoutRequested = true
    }

    /** Consumes a pending request at the LOGOUT phase boundary. */
    fun beginLogout(): Boolean {
        if ((!logoutRequested && !idleLogoutRequested) || loggingOut) return false
        logoutRequested = false
        idleLogoutRequested = false
        loggingOut = true
        return true
    }

    internal fun takeRouteRequest(): RouteRequest? {
        val request = pendingRoute
        pendingRoute = null
        return request
    }

    internal sealed interface RouteRequest {
        val temporaryRun: Boolean?

        /** One ordinary scene-tile movement request. */
        data class Coordinate(
            val destination: Tile,
            override val temporaryRun: Boolean?,
        ) : RouteRequest

        /** One route to the cache-defined operable boundary of a loc. */
        data class Location(
            val target: Loc,
            override val temporaryRun: Boolean?,
        ) : RouteRequest

        /** One route to the exclusive boundary of a pathing entity's current footprint. */
        data class PathingEntity(
            val position: Tile,
            val size: Int,
            override val temporaryRun: Boolean?,
        ) : RouteRequest {
            init {
                require(size in 1..0xFF) { "pathing-entity size must fit an unsigned byte" }
            }
        }
    }

    companion object {
        /** Highest player index addressable by the client player list. */
        const val MAX_CLIENT_INDEX = 2_047

        private val PLAYER_INDEX_RANGE = 1..MAX_CLIENT_INDEX
        private const val MAX_PENDING_GAME_MESSAGES = 8
    }
}
