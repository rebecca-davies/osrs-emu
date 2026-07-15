package emu.server.world

import emu.game.content.player.PlayerContentCatalog
import emu.game.content.ui.UiContentCatalog
import emu.game.pathfinding.PlayerMovementProcess
import emu.game.pathfinding.PlayerRouteFinder
import emu.game.pathfinding.Tile
import emu.game.script.PlayerScriptRunner
import emu.persistence.character.CharacterWriteQueue
import emu.server.world.map.CollisionMapLoader
import emu.server.world.player.PlayerActionProcess
import emu.server.world.player.PlayerChatActionProcess
import emu.server.world.player.PlayerMovementCycleProcess
import emu.server.world.player.PlayerLifecycleProcess
import emu.server.world.player.PlayerScriptProcess
import emu.server.world.player.PlayerTriggerProcess
import emu.server.world.player.RouteSearchBudget
import emu.server.world.config.RouteSearchConfig

/** Shared immutable Kotlin-content runtime used by world tests. */
internal object TestPlayerContent {
    private val repository = PlayerContentCatalog.load(UiContentCatalog.load().components)
    private val runner = PlayerScriptRunner(repository)
    private val triggers = PlayerTriggerProcess(runner)

    fun actions(
        routeFinder: PlayerRouteFinder,
        chat: PlayerChatActionProcess,
        routeSearchesPerCycle: Int = RouteSearchConfig().maxPerCycle,
    ) = PlayerActionProcess(
        routeFinder,
        chat,
        repository,
        runner,
        RouteSearchBudget(RouteSearchConfig(routeSearchesPerCycle)),
    )

    fun scripts() = PlayerScriptProcess(runner, triggers)

    fun lifecycle(writes: CharacterWriteQueue) = PlayerLifecycleProcess(writes, triggers)

    fun triggers() = triggers

    fun movementCycle(movement: PlayerMovementProcess) =
        PlayerMovementCycleProcess(movement, PreparedCollision)

    private object PreparedCollision : CollisionMapLoader {
        override fun prepare(position: Tile) = Unit

        override fun request(position: Tile): Boolean = true
    }
}
