package emu.server.host

import emu.compression.HuffmanCodec
import emu.game.pathfinding.CollisionMap
import emu.persistence.character.CharacterStore
import emu.persistence.chat.ChatAuditSink
import emu.server.world.InProcessWorldServer
import emu.server.world.WorldServer
import emu.server.world.WorldServerDispatchers
import emu.server.world.admission.GameAdmission
import emu.server.world.config.GameExecutionConfig
import emu.server.world.runtime.WorldLifecycle
import emu.server.world.runtime.WorldRuntime
import emu.transport.codec.CodecRepository
import kotlinx.coroutines.withContext
import org.koin.dsl.onClose
import org.koin.dsl.module

/** Defines the world service graph from host-owned runtime capabilities. */
internal fun worldModule(
    codecs: CodecRepository,
    collision: CollisionMap,
    huffman: HuffmanCodec,
    config: GameExecutionConfig,
) = module {
    single { codecs }
    single<CollisionMap> { collision }
    single { huffman }
    single { config }
    single { WorldServerDispatchers(config.ioWorkerThreads) } onClose { it?.close() }
    single { WorldRuntime(maxPlayerIndex = config.maxConcurrentSessions) }
    single {
        val runtime = get<WorldRuntime>()
        WorldLifecycle(get<WorldServerDispatchers>().world, runtime::run)
    }
    single {
        val characters = get<CharacterStore>()
        val dispatchers = get<WorldServerDispatchers>()
        GameAdmission(get<WorldRuntime>(), config.maxConcurrentSessions) { accountId ->
            withContext(dispatchers.io) { characters.load(accountId) }
        }
    }
    single<WorldServer> {
        InProcessWorldServer(
            codecs = get(),
            characters = get(),
            chatAudit = get<ChatAuditSink>(),
            config = get(),
            world = get(),
            collision = get(),
            huffman = get(),
            admissions = get(),
            worldLifecycle = get(),
            dispatchers = get(),
        )
    }
}
