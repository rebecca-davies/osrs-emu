package emu.server.host.composition

import emu.compression.HuffmanCodec
import emu.game.content.areas.inferno.InfernoArena
import emu.game.content.areas.inferno.InfernoFreeModeCatalog
import emu.game.content.beta.BetaWorldContentCatalog
import emu.game.content.player.login.LoginNotices
import emu.game.content.ui.config.UiContent
import emu.game.content.ui.config.UiContentCatalog
import emu.game.loc.LocRepository
import emu.game.map.GameMap
import emu.game.map.Tile
import emu.game.npc.NpcCatalog
import emu.game.npc.NpcList
import emu.game.obj.NamedObjEnumCatalog
import emu.game.obj.ObjCatalog
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
import emu.server.game.network.output.PlayerOutput
import emu.server.game.runtime.command.WorldCommandQueue
import emu.server.game.runtime.lifecycle.WorldLifecycle
import emu.server.game.runtime.lifecycle.WorldRuntime
import emu.server.game.world.World
import emu.server.game.world.cycle.PlayerPhase
import emu.server.game.world.cycle.WorldCycle
import emu.server.game.world.entry.WorldEntry
import emu.server.game.world.map.CacheCollisionMap
import emu.server.game.world.map.CollisionLoadQueue
import emu.server.game.world.map.CollisionMapLoader
import emu.server.game.world.player.PlayerLifecycle
import emu.server.game.world.player.action.PlayerActions
import emu.server.game.world.player.command.buildPlayerCommandRepository
import emu.server.game.world.player.interaction.NpcInteractionTargetResolver
import emu.server.game.world.player.interaction.PlayerInteractionProcess
import emu.transport.codec.CodecRepository
import kotlinx.coroutines.withContext
import org.koin.dsl.module
import org.koin.dsl.onClose

/** Defines the game service graph from host-owned runtime capabilities. */
internal fun gameModule(
    codecs: CodecRepository,
    collision: CacheCollisionMap,
    locs: LocRepository = LocRepository.EMPTY,
    objs: ObjCatalog? = null,
    objEnums: NamedObjEnumCatalog = NamedObjEnumCatalog.EMPTY,
    npcTypes: NpcCatalog = NpcCatalog.EMPTY,
    huffman: HuffmanCodec,
    config: GameExecutionConfig,
) = module {
    single { codecs }
    single { huffman }
    single { config }
    single { config.connection }
    single {
        GameServerDispatchers(
            config.connectionWorkerThreads,
            config.entryWorkerThreads,
        )
    } onClose { it?.close() }

    single { CollisionLoadQueue(collision, config.collisionLoads) } onClose { it?.close() }
    single<CollisionMapLoader> { get<CollisionLoadQueue>() }
    single {
        val collisionLoads = get<CollisionMapLoader>()
        GameMap(
            collision = collision,
            requestAreas = collisionLoads::request,
            locs = locs,
        )
    }
    single { UiContentCatalog.load() }
    single { NpcList() }
    single {
        InfernoArena(
            map = get(),
            types = npcTypes,
            npcs = get(),
            config = InfernoFreeModeCatalog.load(),
        )
    }
    single {
        World(
            map = get(),
            gameframe = get<UiContent>().gameframe,
            loginNotices = LoginNotices.ALL,
            npcs = get(),
            maxPlayerIndex = config.maxConcurrentSessions,
        )
    }
    single { WorldCommandQueue(config.commands) }
    single {
        val ui = get<UiContent>()
        BetaWorldContentCatalog.load(
            ui = ui,
            inferno = get(),
            objs = objs,
            objEnums = objEnums,
        )
    }
    single { PlayerScriptRunner(get()) }
    single { buildPlayerCommandRepository(get()) }
    single { NpcInteractionTargetResolver.usingWorld(get(), get(), npcTypes) }
    single {
        PlayerActions(
            map = get(),
            npcTargets = get(),
            scripts = get(),
            commands = get(),
            chatAudit = get<ChatAuditSink>(),
        )
    }
    single { PlayerPhase(scripts = get()) }
    single { PlayerInteractionProcess(map = get(), scripts = get(), npcTargets = get()) }
    single {
        PlayerLifecycle(
            world = get(),
            writes = get<CharacterWriteQueue>(),
            scripts = get(),
        )
    }
    single { PlayerOutput(world = get(), huffman = get(), gameframe = get<UiContent>().gameframe) }
    single {
        WorldCycle(
            world = get(),
            commands = get(),
            actions = get(),
            interactions = get(),
            playerPhase = get(),
            lifecycle = get(),
            output = get(),
        )
    }
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
