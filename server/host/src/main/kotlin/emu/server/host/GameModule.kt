package emu.server.host

import emu.compression.HuffmanCodec
import emu.game.pathfinding.CollisionMap
import emu.persistence.character.CharacterStore
import emu.persistence.chat.ChatAuditSink
import emu.server.world.GameServer
import emu.server.world.GameService
import emu.server.world.GameServerDispatchers
import emu.server.world.admission.GameAdmission
import emu.server.world.config.GameExecutionConfig
import emu.server.world.runtime.WorldLifecycle
import emu.server.world.runtime.WorldRuntime
import emu.transport.codec.CodecRepository
import kotlinx.coroutines.withContext
import org.koin.dsl.onClose
import org.koin.dsl.module

/** Defines the game service graph from host-owned runtime capabilities. */
internal fun gameModule(
    codecs: CodecRepository,
    collision: CollisionMap,
    huffman: HuffmanCodec,
    config: GameExecutionConfig,
) = module {
    single { codecs }
    single<CollisionMap> { collision }
    single { huffman }
    single { config }
    single { config.connection }
    single { GameServerDispatchers(config.ioWorkerThreads) } onClose { it?.close() }
    single { WorldRuntime(maxPlayerIndex = config.maxConcurrentSessions) }
    single {
        val runtime = get<WorldRuntime>()
        WorldLifecycle(get<GameServerDispatchers>().world, runtime::run)
    }
    single {
        val characters = get<CharacterStore>()
        val dispatchers = get<GameServerDispatchers>()
        GameAdmission(get<WorldRuntime>(), config.maxConcurrentSessions) { accountId ->
            withContext(dispatchers.io) { characters.load(accountId) }
        }
    }
    single<GameService> {
        GameServer(
            codecs = get(),
            characterSaves = get(),
            chatAudit = get<ChatAuditSink>(),
            connectionConfig = get(),
            world = get(),
            collision = get(),
            huffman = get(),
            admissions = get(),
            worldLifecycle = get(),
            dispatchers = get(),
        )
    }
}
