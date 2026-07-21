package emu.server.host.composition

import emu.compression.HuffmanCodec
import emu.game.content.player.PlayerContentCatalog
import emu.game.content.player.login.LoginNotices
import emu.game.content.ui.config.UiContent
import emu.game.content.ui.config.UiContentCatalog
import emu.game.map.Tile
import emu.game.pathfinding.collision.CollisionMap
import emu.game.pathfinding.movement.PlayerMovementProcess
import emu.game.pathfinding.route.PlayerRouteFinder
import emu.game.script.execution.PlayerScriptRunner
import emu.persistence.character.CharacterStore
import emu.persistence.character.write.CharacterWriteQueue
import emu.persistence.chat.ChatAuditSink
import emu.server.game.GameServer
import emu.server.game.GameServerDispatchers
import emu.server.game.GameService
import emu.server.game.config.GameExecutionConfig
import emu.server.game.network.connection.GameConnectionRunner
import emu.server.game.network.input.GameInboundReader
import emu.server.game.runtime.command.WorldCommandQueue
import emu.server.game.runtime.lifecycle.WorldLifecycle
import emu.server.game.runtime.lifecycle.WorldRuntime
import emu.server.game.world.World
import emu.server.game.world.cycle.WorldCycle
import emu.server.game.world.entry.WorldEntry
import emu.server.game.world.map.CacheCollisionMap
import emu.server.game.world.map.CollisionLoadQueue
import emu.server.game.world.map.CollisionMapLoader
import emu.server.game.world.player.process.PlayerActionProcess
import emu.server.game.world.player.process.PlayerChatActionProcess
import emu.server.game.world.player.process.PlayerLifecycleProcess
import emu.server.game.world.player.process.PlayerMainProcess
import emu.server.game.world.player.process.PlayerMovementCycleProcess
import emu.server.game.world.player.process.PlayerOutputProcess
import emu.server.game.world.player.process.PlayerTriggerProcess
import emu.server.game.world.player.route.RouteSearchBudget
import emu.transport.codec.CodecRepository
import kotlinx.coroutines.withContext
import org.koin.dsl.module
import org.koin.dsl.onClose

/** Defines the game service graph from host-owned runtime capabilities. */
internal fun gameModule(
    codecs: CodecRepository,
    collision: CacheCollisionMap,
    huffman: HuffmanCodec,
    config: GameExecutionConfig,
) = module {
    single { codecs }
    single<CollisionMap> { collision }
    single { huffman }
    single { config }
    single { config.connection }
    single {
        GameServerDispatchers(
            config.connectionWorkerThreads,
            config.entryWorkerThreads,
        )
    } onClose { it?.close() }

    single { UiContentCatalog.load() }
    single {
        World(
            get<UiContent>().gameframe,
            LoginNotices.ALL,
            config.maxConcurrentSessions,
        )
    }
    single { WorldCommandQueue(config.commands) }
    single { CollisionLoadQueue(collision, config.collisionLoads) } onClose { it?.close() }
    single<CollisionMapLoader> { get<CollisionLoadQueue>() }
    single { PlayerMovementProcess(get<CollisionMap>()) }
    single<PlayerRouteFinder> { get<PlayerMovementProcess>() }
    single { PlayerMovementCycleProcess(get(), get()) }
    single { PlayerChatActionProcess(get(), get<ChatAuditSink>()) }
    single { RouteSearchBudget(config.routes) }
    single { PlayerContentCatalog.load(get<UiContent>().components) }
    single { PlayerScriptRunner(get()) }
    single { PlayerTriggerProcess(get()) }
    single { PlayerActionProcess(get<PlayerRouteFinder>(), get(), get(), get()) }
    single { PlayerMainProcess(get(), get(), get()) }
    single { PlayerLifecycleProcess(get<CharacterWriteQueue>(), get()) }
    single { PlayerOutputProcess() }
    single { WorldCycle(get(), get(), get(), get(), get(), get()) }
    single { WorldRuntime(get()) }
    single {
        val runtime = get<WorldRuntime>()
        WorldLifecycle(get<GameServerDispatchers>().world, runtime::run)
    }

    single {
        val characters = get<CharacterStore>()
        val dispatchers = get<GameServerDispatchers>()
        val collisionLoads = get<CollisionMapLoader>()
        WorldEntry(get<WorldCommandQueue>(), config.maxConcurrentSessions) { accountId ->
            withContext(dispatchers.entry) {
                characters.load(accountId)?.also { record ->
                    val position = record.position
                    collisionLoads.prepare(Tile(position.x, position.y, position.plane))
                }
            }
        }
    }
    single { GameInboundReader(get(), get(), config.connection.idleTimeout) }
    single {
        GameConnectionRunner(
            codecs = get(),
            config = get(),
            worldCommands = get(),
            inbound = get(),
            ioDispatcher = get<GameServerDispatchers>().connections,
        )
    }
    single<GameService> { GameServer(get(), get(), get()) }
}
