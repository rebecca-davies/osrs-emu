package emu.server.host

import emu.compression.HuffmanCodec
import emu.game.pathfinding.CollisionMap
import emu.game.pathfinding.PlayerMovementProcess
import emu.game.pathfinding.PlayerRouteFinder
import emu.game.pathfinding.Tile
import emu.game.content.player.PlayerContentCatalog
import emu.game.content.player.login.LoginNotices
import emu.game.content.ui.UiContent
import emu.game.content.ui.UiContentCatalog
import emu.game.script.PlayerScriptRunner
import emu.persistence.character.CharacterStore
import emu.persistence.character.CharacterWriteQueue
import emu.persistence.chat.ChatAuditSink
import emu.server.world.GameServer
import emu.server.world.GameServerDispatchers
import emu.server.world.GameService
import emu.server.world.cycle.WorldCycle
import emu.server.world.entry.WorldEntry
import emu.server.world.config.GameExecutionConfig
import emu.server.world.map.CacheCollisionMap
import emu.server.world.map.CollisionLoadQueue
import emu.server.world.map.CollisionMapLoader
import emu.server.world.network.GameConnectionRunner
import emu.server.world.network.GameInboundReader
import emu.server.world.player.PlayerActionProcess
import emu.server.world.player.PlayerChatActionProcess
import emu.server.world.player.PlayerLifecycleProcess
import emu.server.world.player.PlayerMovementCycleProcess
import emu.server.world.player.PlayerOutputProcess
import emu.server.world.player.PlayerScriptProcess
import emu.server.world.player.PlayerTriggerProcess
import emu.server.world.player.RouteSearchBudget
import emu.server.world.runtime.GameWorld
import emu.server.world.runtime.WorldCommandQueue
import emu.server.world.runtime.WorldLifecycle
import emu.server.world.runtime.WorldRuntime
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
        GameWorld(
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
    single { PlayerActionProcess(get<PlayerRouteFinder>(), get(), get(), get(), get()) }
    single { PlayerScriptProcess(get(), get()) }
    single { PlayerLifecycleProcess(get<CharacterWriteQueue>(), get()) }
    single { PlayerOutputProcess() }
    single { WorldCycle(get(), get(), get(), get(), get(), get(), get()) }
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
