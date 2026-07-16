package emu.server.game

import emu.game.content.player.PlayerContentCatalog
import emu.game.content.ui.config.UiContentCatalog
import emu.game.map.Tile
import emu.game.pathfinding.movement.PlayerMovementProcess
import emu.game.pathfinding.route.PlayerRouteFinder
import emu.game.script.execution.PlayerScriptRunner
import emu.persistence.character.write.CharacterWriteQueue
import emu.server.game.config.RouteSearchConfig
import emu.server.game.world.map.CollisionMapLoader
import emu.server.game.world.player.process.PlayerActionProcess
import emu.server.game.world.player.process.PlayerChatActionProcess
import emu.server.game.world.player.process.PlayerLifecycleProcess
import emu.server.game.world.player.process.PlayerMovementCycleProcess
import emu.server.game.world.player.process.PlayerScriptProcess
import emu.server.game.world.player.process.PlayerTriggerProcess
import emu.server.game.world.player.route.RouteSearchBudget

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
